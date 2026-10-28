FROM python:3.12-slim-bookworm
ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    DEMAND_DATA_DIR=/app/runtime \
    OMP_NUM_THREADS=1 \
    OPENBLAS_NUM_THREADS=1
WORKDIR /app
# Runtime/model data is persisted by the ml_data volume in Compose.
# LightGBM requires the OpenMP runtime.
RUN apt-get update && apt-get install -y --no-install-recommends libgomp1 \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system app && useradd --system --gid app app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY service.py demand_model.py ./
RUN mkdir -p /app/runtime && chown -R app:app /app
USER app
EXPOSE 8091
# The service uses an in-process lock and supports exactly one worker.
CMD ["python", "-m", "uvicorn", "service:app", "--host", "0.0.0.0", "--port", "8091", "--workers", "1"]
