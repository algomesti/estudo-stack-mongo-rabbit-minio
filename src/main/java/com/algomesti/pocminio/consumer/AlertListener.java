package com.algomesti.pocminio.consumer;

import com.algomesti.pocminio.model.AlertEntity;
import com.algomesti.pocminio.repository.AlertRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

// Caso o @Slf4j do Lombok falhe, você usaria estes:
// import org.slf4j.Logger;
// import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Slf4j // Certifique-se de que o plugin Lombok está instalado na IDE
@Component
public class AlertListener {

    // Plano B caso o Lombok falhe:
    // private static final Logger log = LoggerFactory.getLogger(AlertListener.class);

    private final AlertRepository repository;
    private final ObjectMapper objectMapper;
    private final MinioClient minioClient;

    @Value("${minio.bucket:evidencias}")
    private String bucketName;

    public AlertListener(AlertRepository repository, MinioClient minioClient) {
        this.repository = repository;
        this.minioClient = minioClient;
        this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    @RabbitListener(queues = "alerts.queue")
    public void process(Message message) {
        try {
            String rawBody = new String(message.getBody());
            log.info(">>> [DEBUG] Conteúdo Bruto do RabbitMQ: {}", rawBody);

            String json = new String(message.getBody());
            log.info(">>> RabbitMQ: Mensagem recebida para processamento.");

            AlertEntity entity = objectMapper.readValue(json, AlertEntity.class);
            String pathNoTemp = entity.getLocalPath();

            if (pathNoTemp == null) {
                log.error(">>> ERRO: localPath nulo no JSON recebido.");
                return;
            }

            File fileTemp = new File(pathNoTemp);
            if (!fileTemp.exists()) {
                log.error(">>> ERRO: Arquivo não encontrado no diretório temporário: {}", pathNoTemp);
                return;
            }

            // [IMPORTANTE] Salvar o tamanho do arquivo para o TUS usar depois
            long fileSize = fileTemp.length();

            // 1. Definir novo nome para o MinIO Local
            String fileNameNoMinio = UUID.randomUUID() + "-" + fileTemp.getName();

            // 2. Upload para o MinIO Local
            log.info(">>> MinIO Local: Enviando arquivo {} para o bucket {}", fileNameNoMinio, bucketName);
            try (FileInputStream fis = new FileInputStream(fileTemp)) {
                minioClient.putObject(
                        PutObjectArgs.builder()
                                .bucket(bucketName)
                                .object(fileNameNoMinio)
                                .stream(fis, fileSize, -1)
                                .contentType("video/mp4")
                                .build()
                );
            }

            // 3. Atualizar a Entidade
            entity.setLocalPath(fileNameNoMinio);
            entity.setFileSize(fileSize); // Agora o CloudSyncJob saberá o tamanho sem perguntar ao MinIO!
            entity.setStatus("PENDENTE");

            repository.save(entity);
            log.info(">>> MongoDB: Alerta salvo com referência ao MinIO: {}", fileNameNoMinio);

            // 4. Limpar arquivo do /tmp
            Path pathToDelete = Paths.get(pathNoTemp);
            Files.delete(pathToDelete);
            log.info(">>> Sistema: Arquivo temporário removido com sucesso: {}", pathNoTemp);

        } catch (Exception e) {
            log.error(">>> Falha Crítica no Processamento do Alerta: {}", e.getMessage());
        }
    }
}