package com.algomesti.pocminio.controller;

import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/videos")
@RequiredArgsConstructor
public class VideoController {

    private final MinioClient minioClient;

    @Value("${minio.bucket}")
    private String bucket;

    @GetMapping("/{videoName}/link")
    public String getStreamingUrl(@PathVariable String videoName) throws Exception {
        // Gera um link que expira em 15 minutos para o player de vídeo
        return minioClient.getPresignedObjectUrl(
                GetPresignedObjectUrlArgs.builder()
                        .method(Method.GET)
                        .bucket(bucket)
                        .object(videoName)
                        .expiry(15, TimeUnit.MINUTES)
                        .build()
        );
    }
}