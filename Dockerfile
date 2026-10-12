FROM eclipse-temurin:17-jdk

run mkdir /app
workdir /app

add ./build/libs/*.jar /app/app.jar

EXPOSE 8088
entrypoint ["java", "-jar", "app.jar"]