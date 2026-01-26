package com.algomesti.pocminio.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;
import java.time.Instant;

@Document(collection = "alerts")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class AlertEntity {
    @Id String id;
    String cameraId;
    String type;
    String status;
    String videoKey;
    @Indexed(expireAfter = "30d")
    private Instant createdAt;

}

