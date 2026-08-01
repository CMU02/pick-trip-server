# jar 는 아키텍처 중립이므로 COPY 만 하는 이미지는 x86 러너에서도 QEMU 없이 arm64 로 빌드된다.
# 배포 대상 EC2(t4g.micro)가 aarch64 이므로 RUN 을 추가하면 에뮬레이션이 필요해진다.
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app
COPY build/libs/*-SNAPSHOT.jar app.jar

# 같은 인스턴스에 다른 서비스가 함께 뜨므로 힙 상한을 건다.
# 비율은 컨테이너 메모리 제한(--memory) 기준이라 호스트가 커져도 힙이 따라 늘지 않는다.
# 컨테이너 기본 TZ 는 UTC 라 JVM 레벨에서 KST 를 지정한다(alpine 에 tzdata 를 깔지 않기 위함).
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=40 -Duser.timezone=Asia/Seoul"

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
