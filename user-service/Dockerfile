FROM node:20-slim

# Create app directory
WORKDIR /usr/src/app

# Install app dependencies
# A wildcard is used to ensure both package.json AND package-lock.json are copied
# where available (npm@5+)
COPY package*.json ./

#RUN apt-get update && apt-get install netcat-openbsd -y

RUN npm install --only=production

# Bundle app source
COPY . .

CMD [ "node", "--env-file=.env", "app.js" ]