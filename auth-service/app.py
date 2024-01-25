import logging.config
import os
import sys

from dotenv import dotenv_values, load_dotenv

from kafka_helper import process_messages
from instrumentation import instrument_log

if not os.getenv('ENV_FILE_LOCATION'):
    os.environ["ENV_FILE_LOCATION"] = ".env"

config = {
    **dotenv_values(os.environ["ENV_FILE_LOCATION"]),  # load shared .env variables
    **os.environ,  # override loaded values with environment variables
}
load_dotenv(override=False)

log = logging.getLogger(__name__)
if config['SET_LOG_FILE'] == 'True':
    logging.basicConfig(
        format="%(asctime)s serviceName='auth-service-python' %(levelname)s [%(name)s %(funcName)s] %(message)s",
        level=config['LOG_LEVEL'],
        handlers=[
            logging.FileHandler(config['LOG_FILE']),
            logging.StreamHandler()
        ],
        datefmt='%Y-%m-%dT%H:%M:%S.%z'
    )
else:
    logging.basicConfig(
        format="%(asctime)s serviceName='auth-service-python' %(levelname)s [%(name)s %(funcName)s] %(message)s",
        level=config['LOG_LEVEL'],
        handlers=[
            logging.StreamHandler(sys.stdout)
        ],
        datefmt='%Y-%m-%dT%H:%M:%S.%z'
    )

root = logging.getLogger()
if config['SET_LOG_FILE'] == 'True':
    file_handler = logging.FileHandler(filename=config['LOG_FILE'])
    root.addHandler(file_handler)


def main():
    service_name = config['OTEL_SERVICE_NAME']
    #instrument_log(service_name)
    log.info("Start consuming messages")
    process_messages(config['PAYMENT_SCHEMA_AVRO_FILE'], config['UPDATE_PAYMENT_SCHEMA_AVRO_FILE'],
                     config['BOOSTRAP_SERVERS'], config['GROUP_ID'], config['PAYMENT_TOPIC'],
                     config['UPDATE_PAYMENT_TOPIC'], config['SCHEMA_REGISTRY_URL'], service_name)


if __name__ == '__main__':
    main()