FROM eclipse-temurin:25-jdk

ENV TZ=Asia/Tbilisi

WORKDIR /app

COPY ./src /app/src

RUN javac -d out ./src/main/java/com/github/konstantinevashalomidze/Main.java

CMD ["java", "-cp", "./out", "com.github.konstantinevashalomidze.Main"]