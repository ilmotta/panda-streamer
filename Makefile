build:
	DOCKER_BUILDKIT=1 docker build --tag panda-streamer:main .

run:
	DOCKER_BUILDKIT=1 docker run --rm -it panda-streamer:main

sh:
	@docker run --rm -it panda-streamer:main sh

.PHONY: build
