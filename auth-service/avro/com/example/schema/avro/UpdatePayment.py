# -*- coding: utf-8 -*-

""" avro python class for file: UpdatePayment """
import datetime
import json
from avro.helpers import default_json_serialize, todict
from typing import Union
from avro.com.example.schema.avro.CheckStatus import CheckStatus


class UpdatePayment(object):

    schema = """
    {
        "type": "record",
        "name": "UpdatePayment",
        "namespace": "com.example.schema.avro",
        "fields": [
            {
                "name": "paymentId",
                "type": "string"
            },
            {
                "name": "updateAt",
                "type": {
                    "type": "long",
                    "logicalType": "timestamp-millis"
                }
            },
            {
                "name": "reasonFailed",
                "type": [
                    "null",
                    "string"
                ]
            },
            {
                "name": "status",
                "type": {
                    "type": "enum",
                    "name": "CheckStatus",
                    "symbols": [
                        "SANCTION_CHECK",
                        "AUTH_CHECK",
                        "USER_CONFIRMATION_CHECK"
                    ],
                    "namespace": "com.example.schema.avro"
                }
            },
            {
                "name": "checkFailed",
                "type": "boolean",
                "default": true
            }
        ]
    }
    """

    def __init__(self, obj: Union[str, dict, 'UpdatePayment']) -> None:
        if isinstance(obj, str):
            obj = json.loads(obj)

        elif isinstance(obj, type(self)):
            obj = obj.__dict__

        elif not isinstance(obj, dict):
            raise TypeError(
                f"{type(obj)} is not in ('str', 'dict', 'UpdatePayment')"
            )

        self.set_paymentId(obj.get('paymentId', None))

        self.set_updateAt(obj.get('updateAt', None))

        self.set_reasonFailed(obj.get('reasonFailed', None))

        self.set_status(obj.get('status', None))

        self.set_checkFailed(obj.get('checkFailed', True))

    def dict(self):
        return todict(self)

    def set_paymentId(self, value: str) -> None:

        if isinstance(value, str):
            self.paymentId = value
        else:
            raise TypeError("field 'paymentId' should be type str")

    def get_paymentId(self) -> str:

        return self.paymentId

    def set_updateAt(self, value: datetime) -> None:

        if isinstance(value, datetime.datetime):
            self.updateAt = value
        else:
            raise TypeError("field 'updateAt' should be type datetime")

    def get_updateAt(self) -> datetime:

        return self.updateAt

    def set_reasonFailed(self, value: Union[None, str]) -> None:
        if isinstance(value, type(None)):
            self.reasonFailed = None

        elif isinstance(value, str):
            self.reasonFailed = str(value)
        else:
            raise TypeError("field 'reasonFailed' should be in (None, str)")

    def get_reasonFailed(self) -> Union[None, str]:
        return self.reasonFailed

    def set_status(self, values: CheckStatus) -> None:

        self.status = CheckStatus(values)

    def get_status(self) -> CheckStatus:

        return self.status

    def set_checkFailed(self, value: bool) -> None:

        if isinstance(value, bool):
            self.checkFailed = value
        else:
            raise TypeError("field 'checkFailed' should be type bool")

    def get_checkFailed(self) -> bool:

        return self.checkFailed

    def serialize(self) -> None:
        return json.dumps(self, default=default_json_serialize)
