package com.algomesti.pocminio.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
@Document(collection = "alerts")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertEntity {
    @Id
    private String id;
    private String cameraId;
    private String type;
    private String localPath;
    private String videoKey;

    // --- ADICIONE ESTA LINHA ---
    private Long fileSize; // Tamanho em bytes para controle do TUS
    // ---------------------------

    @Builder.Default
    private String status = "PENDENTE";

    @Builder.Default
    private Instant createdAt = Instant.now();
}