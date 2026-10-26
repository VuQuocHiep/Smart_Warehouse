# frontend
```
cd frontend
npm run dev
```


# backend
```
cd backend/iot
$env:DB_USERNAME = 'root'
$env:DB_PASSWORD = 'your_password'
$env:DEMAND_SERVICE_ENABLED = 'true'
$env:DEMAND_SERVICE_URL = 'http://127.0.0.1:8091'
$env:DEMAND_SERVICE_TOKEN = 'your_token'

.\mvnw.cmd spring-boot:run

```


# python
```
$env:DEMAND_SERVICE_TOKEN = 'your_token'
.\AI\demand\start.ps1
```