FROM clojure
COPY . /usr/src/app
WORKDIR /usr/src/app
CMD ["lein", "run", "--config_path=config.edn"]