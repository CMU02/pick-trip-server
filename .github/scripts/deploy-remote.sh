#!/bin/sh
# EC2 에서 SSM Run Command(root)로 실행되는 배포 스크립트.
# deploy.yml 이 IMAGE, SHA, REQUIRED_KEYS 변수 정의를 이 파일 앞에 붙여 보낸다.
set -e

# application-prod.yaml 이 요구하는 키가 운영 .env 에 모두 있는지 확인한다.
# 구버전 컨테이너를 건드리기 전에 검사해야 누락 시 서비스가 무중단으로 유지된다.
MISSING=""
for key in $REQUIRED_KEYS; do
  grep -q "^${key}=" /opt/picktrip/.env || MISSING="$MISSING $key"
done
if [ -n "$MISSING" ]; then
  echo "운영 .env 에 누락된 키:$MISSING"
  exit 1
fi

# GHCR 패키지가 public 이라 로그인 없이 받는다. private 으로 바꾸면 여기서 인증이 필요하다.
docker pull "$IMAGE:$SHA"

# 롤백: 신규 컨테이너를 제거하고 구버전을 원래 이름으로 되돌려 재기동한다.
rollback() {
  docker rm -f picktrip 2>/dev/null || true
  docker rename picktrip-old picktrip 2>/dev/null || true
  docker start picktrip 2>/dev/null || true
}

# 이전 배포가 실패하며 남긴 picktrip-old 가 있으면 rename 이 실패하므로 먼저 지운다.
docker rm -f picktrip-old 2>/dev/null || true

# 구버전을 즉시 삭제하지 않고 보존한다. 신규 기동이 실패하면 이 컨테이너로 되돌린다.
docker rename picktrip picktrip-old 2>/dev/null || true
docker stop picktrip-old 2>/dev/null || true

# 8081 은 루프백에만 바인딩한다. 외부 노출은 nginx 가 담당하므로 보안그룹 변경이 필요 없다.
docker run -d --name picktrip --restart unless-stopped \
  -p 127.0.0.1:8081:8080 \
  --memory=1g \
  --log-opt max-size=10m --log-opt max-file=3 \
  --env-file /opt/picktrip/.env \
  -e SPRING_PROFILES_ACTIVE=prod \
  "$IMAGE:$SHA" || { echo "컨테이너 기동 실패"; rollback; exit 1; }

for i in $(seq 1 36); do
  if curl -sf http://127.0.0.1:8081/actuator/health > /dev/null; then
    echo "헬스체크 통과"

    # 구버전을 제거하고, 이전 태그 이미지만 정리한다.
    # prune -af 는 호스트 전역이라 같은 EC2 의 다른 프로젝트 이미지까지 지우므로
    # 이 이미지 저장소의 최신/현재 태그를 제외한 태그만 좁혀서 지운다.
    docker rm -f picktrip-old 2>/dev/null || true
    docker images "$IMAGE" --format '{{.Repository}}:{{.Tag}}' \
      | grep -v ":latest$" | grep -v ":$SHA$" \
      | xargs -r docker rmi 2>/dev/null || true

    exit 0
  fi
  sleep 5
done

echo "헬스체크 실패 - 최근 로그"
docker logs --tail 100 picktrip

rollback
exit 1
