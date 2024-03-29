// Code generated by github.com/actgardner/gogen-avro/v10. DO NOT EDIT.
/*
 * SOURCE:
 *     payment-schema.avsc
 */
package avro

import (
	"encoding/json"
	"fmt"
	"io"

	"github.com/actgardner/gogen-avro/v10/compiler"
	"github.com/actgardner/gogen-avro/v10/vm"
	"github.com/actgardner/gogen-avro/v10/vm/types"
)

var _ = fmt.Printf

type Payment struct {
	RequestId string `json:"requestId"`

	CreatedAt int64 `json:"createdAt"`

	Status string `json:"status"`

	Total Bytes `json:"total"`

	UsernameFrom string `json:"usernameFrom"`

	UsernameFromAddress string `json:"usernameFromAddress"`

	UsernameTo string `json:"usernameTo"`

	UsernameToAddress string `json:"usernameToAddress"`
}

const PaymentAvroCRC64Fingerprint = "\xca\n\xa7\ry6ا"

func NewPayment() Payment {
	r := Payment{}
	return r
}

func DeserializePayment(r io.Reader) (Payment, error) {
	t := NewPayment()
	deser, err := compiler.CompileSchemaBytes([]byte(t.Schema()), []byte(t.Schema()))
	if err != nil {
		return t, err
	}

	err = vm.Eval(r, deser, &t)
	return t, err
}

func DeserializePaymentFromSchema(r io.Reader, schema string) (Payment, error) {
	t := NewPayment()

	deser, err := compiler.CompileSchemaBytes([]byte(schema), []byte(t.Schema()))
	if err != nil {
		return t, err
	}

	err = vm.Eval(r, deser, &t)
	return t, err
}

func writePayment(r Payment, w io.Writer) error {
	var err error
	err = vm.WriteString(r.RequestId, w)
	if err != nil {
		return err
	}
	err = vm.WriteLong(r.CreatedAt, w)
	if err != nil {
		return err
	}
	err = vm.WriteString(r.Status, w)
	if err != nil {
		return err
	}
	err = vm.WriteBytes(r.Total, w)
	if err != nil {
		return err
	}
	err = vm.WriteString(r.UsernameFrom, w)
	if err != nil {
		return err
	}
	err = vm.WriteString(r.UsernameFromAddress, w)
	if err != nil {
		return err
	}
	err = vm.WriteString(r.UsernameTo, w)
	if err != nil {
		return err
	}
	err = vm.WriteString(r.UsernameToAddress, w)
	if err != nil {
		return err
	}
	return err
}

func (r Payment) Serialize(w io.Writer) error {
	return writePayment(r, w)
}

func (r Payment) Schema() string {
	return "{\"fields\":[{\"name\":\"requestId\",\"type\":\"string\"},{\"name\":\"createdAt\",\"type\":{\"logicalType\":\"timestamp-millis\",\"type\":\"long\"}},{\"name\":\"status\",\"type\":\"string\"},{\"name\":\"total\",\"type\":{\"logicalType\":\"decimal\",\"precision\":18,\"scale\":2,\"type\":\"bytes\"}},{\"name\":\"usernameFrom\",\"type\":\"string\"},{\"name\":\"usernameFromAddress\",\"type\":\"string\"},{\"name\":\"usernameTo\",\"type\":\"string\"},{\"name\":\"usernameToAddress\",\"type\":\"string\"}],\"name\":\"com.example.schema.avro.Payment\",\"type\":\"record\"}"
}

func (r Payment) SchemaName() string {
	return "com.example.schema.avro.Payment"
}

func (_ Payment) SetBoolean(v bool)    { panic("Unsupported operation") }
func (_ Payment) SetInt(v int32)       { panic("Unsupported operation") }
func (_ Payment) SetLong(v int64)      { panic("Unsupported operation") }
func (_ Payment) SetFloat(v float32)   { panic("Unsupported operation") }
func (_ Payment) SetDouble(v float64)  { panic("Unsupported operation") }
func (_ Payment) SetBytes(v []byte)    { panic("Unsupported operation") }
func (_ Payment) SetString(v string)   { panic("Unsupported operation") }
func (_ Payment) SetUnionElem(v int64) { panic("Unsupported operation") }

func (r *Payment) Get(i int) types.Field {
	switch i {
	case 0:
		w := types.String{Target: &r.RequestId}

		return w

	case 1:
		w := types.Long{Target: &r.CreatedAt}

		return w

	case 2:
		w := types.String{Target: &r.Status}

		return w

	case 3:
		w := BytesWrapper{Target: &r.Total}

		return w

	case 4:
		w := types.String{Target: &r.UsernameFrom}

		return w

	case 5:
		w := types.String{Target: &r.UsernameFromAddress}

		return w

	case 6:
		w := types.String{Target: &r.UsernameTo}

		return w

	case 7:
		w := types.String{Target: &r.UsernameToAddress}

		return w

	}
	panic("Unknown field index")
}

func (r *Payment) SetDefault(i int) {
	switch i {
	}
	panic("Unknown field index")
}

func (r *Payment) NullField(i int) {
	switch i {
	}
	panic("Not a nullable field index")
}

func (_ Payment) AppendMap(key string) types.Field { panic("Unsupported operation") }
func (_ Payment) AppendArray() types.Field         { panic("Unsupported operation") }
func (_ Payment) HintSize(int)                     { panic("Unsupported operation") }
func (_ Payment) Finalize()                        {}

func (_ Payment) AvroCRC64Fingerprint() []byte {
	return []byte(PaymentAvroCRC64Fingerprint)
}

func (r Payment) MarshalJSON() ([]byte, error) {
	var err error
	output := make(map[string]json.RawMessage)
	output["requestId"], err = json.Marshal(r.RequestId)
	if err != nil {
		return nil, err
	}
	output["createdAt"], err = json.Marshal(r.CreatedAt)
	if err != nil {
		return nil, err
	}
	output["status"], err = json.Marshal(r.Status)
	if err != nil {
		return nil, err
	}
	output["total"], err = json.Marshal(r.Total)
	if err != nil {
		return nil, err
	}
	output["usernameFrom"], err = json.Marshal(r.UsernameFrom)
	if err != nil {
		return nil, err
	}
	output["usernameFromAddress"], err = json.Marshal(r.UsernameFromAddress)
	if err != nil {
		return nil, err
	}
	output["usernameTo"], err = json.Marshal(r.UsernameTo)
	if err != nil {
		return nil, err
	}
	output["usernameToAddress"], err = json.Marshal(r.UsernameToAddress)
	if err != nil {
		return nil, err
	}
	return json.Marshal(output)
}

func (r *Payment) UnmarshalJSON(data []byte) error {
	var fields map[string]json.RawMessage
	if err := json.Unmarshal(data, &fields); err != nil {
		return err
	}

	var val json.RawMessage
	val = func() json.RawMessage {
		if v, ok := fields["requestId"]; ok {
			return v
		}
		return nil
	}()

	if val != nil {
		if err := json.Unmarshal([]byte(val), &r.RequestId); err != nil {
			return err
		}
	} else {
		return fmt.Errorf("no value specified for requestId")
	}
	val = func() json.RawMessage {
		if v, ok := fields["createdAt"]; ok {
			return v
		}
		return nil
	}()

	if val != nil {
		if err := json.Unmarshal([]byte(val), &r.CreatedAt); err != nil {
			return err
		}
	} else {
		return fmt.Errorf("no value specified for createdAt")
	}
	val = func() json.RawMessage {
		if v, ok := fields["status"]; ok {
			return v
		}
		return nil
	}()

	if val != nil {
		if err := json.Unmarshal([]byte(val), &r.Status); err != nil {
			return err
		}
	} else {
		return fmt.Errorf("no value specified for status")
	}
	val = func() json.RawMessage {
		if v, ok := fields["total"]; ok {
			return v
		}
		return nil
	}()

	if val != nil {
		if err := json.Unmarshal([]byte(val), &r.Total); err != nil {
			return err
		}
	} else {
		return fmt.Errorf("no value specified for total")
	}
	val = func() json.RawMessage {
		if v, ok := fields["usernameFrom"]; ok {
			return v
		}
		return nil
	}()

	if val != nil {
		if err := json.Unmarshal([]byte(val), &r.UsernameFrom); err != nil {
			return err
		}
	} else {
		return fmt.Errorf("no value specified for usernameFrom")
	}
	val = func() json.RawMessage {
		if v, ok := fields["usernameFromAddress"]; ok {
			return v
		}
		return nil
	}()

	if val != nil {
		if err := json.Unmarshal([]byte(val), &r.UsernameFromAddress); err != nil {
			return err
		}
	} else {
		return fmt.Errorf("no value specified for usernameFromAddress")
	}
	val = func() json.RawMessage {
		if v, ok := fields["usernameTo"]; ok {
			return v
		}
		return nil
	}()

	if val != nil {
		if err := json.Unmarshal([]byte(val), &r.UsernameTo); err != nil {
			return err
		}
	} else {
		return fmt.Errorf("no value specified for usernameTo")
	}
	val = func() json.RawMessage {
		if v, ok := fields["usernameToAddress"]; ok {
			return v
		}
		return nil
	}()

	if val != nil {
		if err := json.Unmarshal([]byte(val), &r.UsernameToAddress); err != nil {
			return err
		}
	} else {
		return fmt.Errorf("no value specified for usernameToAddress")
	}
	return nil
}
