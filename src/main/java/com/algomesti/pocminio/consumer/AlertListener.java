package com.algomesti.pocminio.consumer;

import com.algomesti.pocminio.model.AlertEntity;
import com.algomesti.pocminio.model.AlertMessage;
import com.algomesti.pocminio.repository.AlertRepository;
import io.minio.MinioClient;
import io.minio.UploadObjectArgs;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component @Slf4j
public class AlertListener {
    private final AlertRepository repository;
    private final MinioClient minioClient;
    @Value("${minio.bucket}") private String bucket;

    public AlertListener(AlertRepository repository, MinioClient minioClient) {
        this.repository = repository;
        this.minioClient = minioClient;
    }

    @RabbitListener(queues = "alerts.queue")
    public void onAlert(AlertMessage msg) {
        try {
            // Correção: Usamos o timestamp da mensagem ou Instant.now() diretamente
            Instant alertTime = (msg.timestamp() != null) ? msg.timestamp() : Instant.now();

            AlertEntity entity = repository.save(AlertEntity.builder()
                    .cameraId(msg.cameraId())
                    .type(msg.type())
                    .status("PENDENTE")
                    .createdAt(alertTime) // Uso correto do Instant
                    .build());

            minioClient.uploadObject(UploadObjectArgs.builder()
                    .bucket(bucket)
                    .object(entity.getId() + ".mp4")
                    .filename(msg.localPath())
                    .build());

            entity.setVideoKey(entity.getId() + ".mp4");
            repository.save(entity);
            log.info("Sucesso! Alerta salvo no DocumentDB e MinIO: {}", entity.getId());
        } catch (Exception e) {
            log.error("Erro crítico no processamento de evidência do navio", e);
        }
    }
}