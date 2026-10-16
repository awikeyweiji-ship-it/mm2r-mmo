FROM python:3.12-slim
WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY app ./app
COPY run_server.py run_worker.py ./
ENV AGENT_DATA_DIR=/data
ENV AGENT_AUTOSTART_WORKER=true
VOLUME ["/data"]
EXPOSE 8088
CMD ["python","run_server.py"]
