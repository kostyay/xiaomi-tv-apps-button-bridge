.PHONY: build lint fmt fml

build:
	./gradlew assembleDebug

lint:
	./gradlew ktlintCheck detekt lintDebug

fmt:
	./gradlew ktlintFormat

fml: fmt
