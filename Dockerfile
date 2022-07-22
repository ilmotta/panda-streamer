# syntax=docker/dockerfile:1
FROM clojure:tools-deps-1.11.1.1149

RUN apt-get update \
    && apt-get install -y curl \
    && curl -sL https://deb.nodesource.com/setup_16.x | bash \
    && apt-get install -y nodejs

WORKDIR /usr/src/app

COPY ./package-lock.json ./package.json ./
RUN npm ci

COPY ./deps.edn ./
RUN clojure -P

COPY . .

CMD ["npm", "run", "release"]
