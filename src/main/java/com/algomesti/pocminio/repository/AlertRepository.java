package com.algomesti.pocminio.repository;

import com.algomesti.pocminio.model.AlertEntity;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import java.util.List; // Não esqueça do import do List

@Repository
public interface AlertRepository extends MongoRepository<AlertEntity, String> {

    // Adicione esta linha:
    List<AlertEntity> findByStatus(String status);
}