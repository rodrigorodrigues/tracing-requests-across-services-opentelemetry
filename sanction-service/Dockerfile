FROM --platform=linux/amd64 golang:1.21.2-alpine3.18

ARG ENV

ENV ENVIRONMENT=$ENV

RUN apk update && apk add gcc g++ libc-dev librdkafka-dev pkgconf musl-dev

WORKDIR /app

# Download Go modules
COPY go.mod go.sum ./

RUN go mod download && go mod verify

COPY . .

ENV GOOS=linux

ENV GOARCH=amd64

ENV CGO_ENABLED=1

RUN go build -tags musl --ldflags "-extldflags -static" -o main .

CMD ["/app/main"]
