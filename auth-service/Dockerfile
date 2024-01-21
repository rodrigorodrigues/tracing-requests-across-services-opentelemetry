FROM python:3.9

COPY . /app

RUN mv /app/.env.docker /app/.env

WORKDIR /app

RUN pip install -r requirements.txt

ENTRYPOINT [ "python" ]

CMD [ "app.py" ]