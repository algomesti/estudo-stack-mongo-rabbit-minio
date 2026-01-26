package com.algomesti.pocminio.repository;

import com.algomesti.pocminio.model.AlertEntity;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AlertRepository extends MongoRepository<AlertEntity, String> {}