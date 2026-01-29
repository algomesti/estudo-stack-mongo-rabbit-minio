package com.algomesti.pocminio.job;

import com.algomesti.pocminio.model.AlertEntity;
import com.algomesti.pocminio.repository.AlertRepository;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.tus.java.client.*;
import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.quartz.QuartzJobBean;
import org.springframework.stereotype.Component;

import io.tus.java.client.ProtocolException;

import java.io.InputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.io.IOException; // Esta é a que está faltando e causando o erro vermelho

@Slf4j
@Component
@DisallowConcurrentExecution
public class CloudSyncJob extends QuartzJobBean {

    @Autowired private AlertRepository repository;
    @Autowired private MinioClient minioLocal;

    @Value("${cloud.sync.url:http://localhost:9080/alerts/sync/}")
    private String cloudUrl;

    @Override
    protected void executeInternal(JobExecutionContext context) {
        List<AlertEntity> pendentes = repository.findByStatus("PENDENTE");
        if (pendentes.isEmpty()) return;

        try {
            TusClient client = new TusClient();
            URL creationUrl = new URL(cloudUrl);
            client.setUploadCreationURL(creationUrl);

            log.info(">>> [TUS] Tentando conectar em: {}", creationUrl.toString());

            // Store em memória para manter a URL de upload durante a execução
            client.enableResuming(new TusURLMemoryStore());

            for (AlertEntity alert : pendentes) {
                log.info(">>> [TUS] Iniciando sincronismo: {}", alert.getLocalPath());
                processUpload(client, alert);
            }
        } catch (Exception e) {
            log.error(">>> [TUS] Erro crítico no motor de sincronismo: {}", e.getMessage());
        }
    }

    private void processUpload(TusClient client, AlertEntity alert) throws Exception {
        // 1. Obter o tamanho real do arquivo no MinIO Local
        StatObjectResponse stat = minioLocal.statObject(
                StatObjectArgs.builder()
                        .bucket("evidencias")
                        .object(alert.getLocalPath())
                        .build());

        // 2. Abrir o Stream
        try (InputStream is = minioLocal.getObject(
                GetObjectArgs.builder()
                        .bucket("evidencias")
                        .object(alert.getLocalPath())
                        .build())) {

            // 3. Configurar o Upload
            TusUpload upload = new TusUpload();
            upload.setInputStream(is);
            upload.setSize(stat.size());

            Map<String, String> metadata = new HashMap<>();
            metadata.put("filename", alert.getLocalPath());
            metadata.put("alertId", alert.getId());
            upload.setMetadata(metadata);

            // 4. Executor com retentativa nativa
            TusExecutor executor = new TusExecutor() {
                @Override
                protected void makeAttempt() throws ProtocolException, IOException { // AJUSTADO AQUI
                    try {
                        TusUploader uploader = client.resumeOrCreateUpload(upload);
                        uploader.setChunkSize(256 * 1024); // Chunks de 1MB

                        log.info(">>> [NAVIO] Iniciando envio em pedaços de {} bytes", uploader.getChunkSize());

                        while (uploader.uploadChunk() > -1) {
                            long totalSend = uploader.getOffset();
                            long sizeTotal = upload.getSize();
                            double progress = (totalSend * 100.0) / sizeTotal;

                            log.info(">>> [NAVIO] Enviando Chunk... [ {} / {} bytes ] - {}%",
                                    totalSend, sizeTotal, String.format("%.2f", progress));
                        }

                        uploader.finish();
                    } catch (Exception e) {
                        // Se houver erro interno de lógica, precisamos converter
                        // ou relançar para o executor entender a falha na tentativa
                        log.error(">>> Erro durante tentativa de upload: {}", e.getMessage());
                        throw e;
                    }
                }
            };

            if (executor.makeAttempts()) {
                alert.setStatus("SINCRONIZADO");
                repository.save(alert);
                log.info(">>> [TUS] Sucesso total: {}", alert.getLocalPath());
            }
        }
    }
}