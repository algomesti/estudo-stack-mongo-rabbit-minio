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
@Builder // Adicionado para resolver o erro do builder()
@NoArgsConstructor // Necessário para o Jackson e MongoDB
@AllArgsConstructor // Necessário para o Builder
public class AlertEntity {
    @Id
    private String id;
    private String cameraId;
    private String type;
    private String localPath;
    private String videoKey; // Adicionado para resolver o erro do setVideoKey

    @Builder.Default
    private String status = "PENDENTE";

    @Builder.Default
    private Instant createdAt = Instant.now(); // Mude para Instant
}