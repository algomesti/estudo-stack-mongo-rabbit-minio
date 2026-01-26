# POC Java - MongoDB - Rabbit - MinIO
Poc para testar o processo de consumo de fila Rabbit e gravação de arquivo no MinIO e dos metadados do arquivo no MongoDB.

## Container de Apoio
- RabbitMq
- MongoDb
- MinIO

### Levantar containers
```
cd docker
docker compose up -d
```
#### Observação
1. Quando levantar a aplicação, o java já vai criar a fila no RabbitMQ
2. O MinIO ja sobe com regra de retenção de 30 dias, e ja cria um bucket chamado evidencias
3. O documento no mongoDb é gravado sem ttl. (Pode ser criado o índice manualmente)
 

## Levantar Aplicação
``` 
mvn spring-boot:run
```

## Acessos aos recursos:
### MongoDB
- **host :** localhost
- **port :** 27017
- **login :** admin
- **password :** password123
- **database :** algomesti_db
- **uri:** mongodb://admin:password123@localhost:27017/algomesti_db?authSource=admin

### RabbitMq
- **host :** localhost
- **port :** 5672
- **username :** admin
- **password :** password123

### MinIO
- **host :** localhost
- **port :** 9001
- **login :** admin
- **password :** password123

## Testes
- Na pasta videos existem 3 exemplos de vídeo.  Copiar os arquivo de video para a pasta temp 
```
Na pasta root do projeto
cp ./videos/* /tmp
```

- Executar os curls a seguir para jogar mensagens no rabbit:
```
curl -i -u admin:password123 -H "content-type:application/json" \
    -XPOST http://localhost:15672/api/exchanges/%2f/amq.default/publish \
    -d '{
        "properties":{},
        "routing_key":"alerts.queue",
        "payload":"{\"cameraId\":\"CAM-01\",\"type\":\"INVASAO\",\"localPath\":\"/tmp/video1.mp4\",\"timestamp\":\"2026-01-26T14:30:00Z\"}",
        "payload_encoding":"string"
    }'
```
```
curl -i -u admin:password123 -H "content-type:application/json" \
    -XPOST http://localhost:15672/api/exchanges/%2f/amq.default/publish \
    -d '{
        "properties":{},
        "routing_key":"alerts.queue",
        "payload":"{\"cameraId\":\"CAM-01\",\"type\":\"SUSPEITA\",\"localPath\":\"/tmp/video2.mp4\",\"timestamp\":\"2026-01-26T12:30:00Z\"}",
        "payload_encoding":"string"
    }'
```
- Verifique no Mongo se os registros foram inseridos corretamente
- Verifique no MinIO se os arquivos entraram no bucket do MinIO

### Como gerar a url para dar play no video:
- Execute a seguinte url:
```
http://localhost:8080/api/videos/<id do arquivo>/link
```

- Exemplo:

http://localhost:8080/api/videos/6977f0242c388c78352c7781.mp4/link


 
