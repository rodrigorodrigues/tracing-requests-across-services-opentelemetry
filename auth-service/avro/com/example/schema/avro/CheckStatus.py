# -*- coding: utf-8 -*-

""" avro python class for file: CheckStatus """

import json
from enum import Enum
from avro.helpers import default_json_serialize, DefaultEnumMeta, todict


class CheckStatus(Enum, metaclass=DefaultEnumMeta):

    schema = """
    {
        "type": "enum",
        "name": "CheckStatus",
        "symbols": [
            "SANCTION_CHECK",
            "AUTH_CHECK",
            "USER_CONFIRMATION_CHECK"
        ],
        "namespace": "com.example.schema.avro"
    }
    """

    SANCTION_CHECK = 'SANCTION_CHECK'
    AUTH_CHECK = 'AUTH_CHECK'
    USER_CONFIRMATION_CHECK = 'USER_CONFIRMATION_CHECK'

    def encode(self):
        return self.name

    def serialize(self) -> None:
        return json.dumps(self, default=default_json_serialize)
