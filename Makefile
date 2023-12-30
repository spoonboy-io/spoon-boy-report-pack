.PHONY: build

build:
	docker run -it --rm -u root -v $(PWD):/home/gradle/spoon-boy-report-pack -w /home/gradle/spoon-boy-report-pack gradle:8.5-jdk11 gradle build
	cp ./build/libs/* ./plugin/.
	rm -r ./build

