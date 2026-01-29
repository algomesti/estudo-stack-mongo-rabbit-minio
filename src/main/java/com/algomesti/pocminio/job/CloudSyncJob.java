package com.algomesti.pocminio.job;

import com.algomesti.pocminio.model.AlertEntity;
import com.algomesti.pocminio.repository.AlertRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.*;
import org.springframework.scheduling.quartz.QuartzJobBean;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.io.InputStream;
import java.util.List;

@Slf4j
@Component
@DisallowConcurrentExecution
public class CloudSyncJob extends QuartzJobBean {

    @Autowired private AlertRepository repository;
    @Autowired private MinioClient minioClient; // Injetamos o cliente local

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());

    @Value("${minio.bucket:evidencias}")
    private String localBucket;

    @Override
    protected void executeInternal(JobExecutionContext context) {
        List<AlertEntity> pendentes = repository.findByStatus("PENDENTE");
        if (pendentes.isEmpty()) return;

        log.info("Quartz: Sincronizando {} alertas do MinIO Local para a Nuvem...", pendentes.size());

        for (AlertEntity alert : pendentes) {
            try {
                enviarParaNuvem(alert);
                alert.setStatus("SINCRONIZADO");
                repository.save(alert);
                log.info(">>> SUCESSO: Alerta {} sincronizado na Nuvem!", alert.getId());
            } catch (Exception e) {
                log.error(">>> FALHA na sincronização do alerta {}: {}", alert.getId(), e.getMessage());
                break;
            }
        }
    }

    private void enviarParaNuvem(AlertEntity alert) throws Exception {
        String cloudUrl = "http://localhost:9080/alerts/sync";
        String objectName = alert.getLocalPath(); // Agora é o nome do objeto no MinIO

        // 1. "Pescar" o arquivo no MinIO Local
        InputStream stream = minioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(localBucket)
                        .object(objectName)
                        .build()
        );

        // 2. Montar a requisição Multipart
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();

        // Parte JSON
        HttpHeaders jsonHeaders = new HttpHeaders();
        jsonHeaders.setContentType(MediaType.APPLICATION_JSON);
        body.add("alert", new HttpEntity<>(objectMapper.writeValueAsString(alert), jsonHeaders));

        // Parte Arquivo (Usando InputStreamResource com o stream do MinIO)
        body.add("file", new InputStreamResource(stream) {
            @Override
            public String getFilename() {
                return objectName; // O Spring precisa de um nome para o multipart
            }
            @Override
            public long contentLength() {
                return -1; // Stream do MinIO não sabe o tamanho de antemão facilmente
            }
        });

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        // 3. Postar para a Nuvem
        ResponseEntity<String> response = restTemplate.postForEntity(cloudUrl, requestEntity, String.class);

        if (response.getStatusCode() != HttpStatus.OK) {
            throw new RuntimeException("Nuvem recusou o arquivo: " + response.getStatusCode());
        }

        stream.close(); // Fechar o duto de dados
    }
}