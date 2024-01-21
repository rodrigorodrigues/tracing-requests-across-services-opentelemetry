# -*- coding: utf-8 -*-

""" avro python class for file: Payment """
import datetime
import decimal
import json
from avro.helpers import default_json_serialize, todict
from typing import Union


class Payment(object):

    schema = """
    {
        "type": "record",
        "name": "Payment",
        "namespace": "com.example.schema.avro",
        "fields": [
            {
                "name": "requestId",
                "type": "string"
            },
            {
                "name": "createdAt",
                "type": {
                    "type": "long",
                    "logicalType": "timestamp-millis"
                }
            },
            {
                "name": "status",
                "type": "string"
            },
            {
                "name": "total",
                "type": {
                    "type": "bytes",
                    "logicalType": "decimal",
                    "scale": 2,
                    "precision": 18
                }
            },
            {
                "name": "usernameFrom",
                "type": "string"
            },
            {
                "name": "usernameFromAddress",
                "type": "string"
            },
            {
                "name": "usernameTo",
                "type": "string"
            },
            {
                "name": "usernameToAddress",
                "type": "string"
            }
        ]
    }
    """

    def __init__(self, obj: Union[str, dict, 'Payment']) -> None:
        if isinstance(obj, str):
            obj = json.loads(obj)

        elif isinstance(obj, type(self)):
            obj = obj.__dict__

        elif not isinstance(obj, dict):
            raise TypeError(
                f"{type(obj)} is not in ('str', 'dict', 'Payment')"
            )

        self.set_requestId(obj.get('requestId', None))

        self.set_createdAt(obj.get('createdAt', None))

        self.set_status(obj.get('status', None))

        self.set_total(obj.get('total', None))

        self.set_usernameFrom(obj.get('usernameFrom', None))

        self.set_usernameFromAddress(obj.get('usernameFromAddress', None))

        self.set_usernameTo(obj.get('usernameTo', None))

        self.set_usernameToAddress(obj.get('usernameToAddress', None))

    def dict(self):
        return todict(self, classkey=Payment)

    def set_requestId(self, value: str) -> None:

        if isinstance(value, str):
            self.requestId = value
        else:
            raise TypeError("field 'requestId' should be type str")

    def get_requestId(self) -> str:

        return self.requestId

    def set_createdAt(self, value: datetime) -> None:

        if isinstance(value, datetime.datetime):
            self.createdAt = value
        else:
            raise TypeError("field 'createdAt' should be type datetime")

    def get_createdAt(self) -> datetime:

        return self.createdAt

    def set_status(self, value: str) -> None:

        if isinstance(value, str):
            self.status = value
        else:
            raise TypeError("field 'status' should be type str")

    def get_status(self) -> str:

        return self.status

    def set_total(self, value: decimal.Decimal) -> None:

        if isinstance(value, decimal.Decimal):
            self.total = value
        else:
            raise TypeError("field 'total' should be type bytes")

    def get_total(self) -> decimal.Decimal:

        return self.total

    def set_usernameFrom(self, value: str) -> None:

        if isinstance(value, str):
            self.usernameFrom = value
        else:
            raise TypeError("field 'usernameFrom' should be type str")

    def get_usernameFrom(self) -> str:

        return self.usernameFrom

    def set_usernameFromAddress(self, value: str) -> None:

        if isinstance(value, str):
            self.usernameFromAddress = value
        else:
            raise TypeError("field 'usernameFromAddress' should be type str")

    def get_usernameFromAddress(self) -> str:

        return self.usernameFromAddress

    def set_usernameTo(self, value: str) -> None:

        if isinstance(value, str):
            self.usernameTo = value
        else:
            raise TypeError("field 'usernameTo' should be type str")

    def get_usernameTo(self) -> str:

        return self.usernameTo

    def set_usernameToAddress(self, value: str) -> None:

        if isinstance(value, str):
            self.usernameToAddress = value
        else:
            raise TypeError("field 'usernameToAddress' should be type str")

    def get_usernameToAddress(self) -> str:

        return self.usernameToAddress

    def serialize(self) -> None:
        return json.dumps(self, default=default_json_serialize)
