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

import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Slf4j
@Component
public class AlertListener {

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

            // 1. Definir novo nome para o MinIO Local (UUID para evitar duplicidade)
            String fileNameNoMinio = UUID.randomUUID() + "-" + fileTemp.getName();

            // 2. Upload para o MinIO Local (Porta 9000)
            log.info(">>> MinIO Local: Enviando arquivo {} para o bucket {}", fileNameNoMinio, bucketName);
            try (FileInputStream fis = new FileInputStream(fileTemp)) {
                minioClient.putObject(
                        PutObjectArgs.builder()
                                .bucket(bucketName)
                                .object(fileNameNoMinio)
                                .stream(fis, fileTemp.length(), -1)
                                .contentType("video/mp4")
                                .build()
                );
            }

            // 3. Atualizar a Entidade com o caminho do MinIO em vez do Path do /tmp
            // Agora o Quartz vai ler este nome para baixar do MinIO e mandar para a Nuvem
            entity.setLocalPath(fileNameNoMinio);
            entity.setStatus("PENDENTE");

            repository.save(entity);
            log.info(">>> MongoDB: Alerta salvo com referência ao MinIO: {}", fileNameNoMinio);

            // 4. Limpar o Convés: Apagar arquivo do /tmp
            Path pathToDelete = Paths.get(pathNoTemp);
            Files.delete(pathToDelete);
            log.info(">>> Sistema: Arquivo temporário removido com sucesso: {}", pathNoTemp);

        } catch (Exception e) {
            log.error(">>> Falha Crítica no Processamento do Alerta: {}", e.getMessage());
        }
    }
}