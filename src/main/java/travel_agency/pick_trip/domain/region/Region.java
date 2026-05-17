package travel_agency.pick_trip.domain.region;

import travel_agency.pick_trip.gloal.error.ErrorCode;
import travel_agency.pick_trip.gloal.error.exception.ContentException;

import java.util.Arrays;

public enum Region {
  HADONG("하동", "36", "18"),    // 경남 하동군
  YEONGJU("영주", "35", "14"),   // 경북 영주시
  YECHEON("예천", "35", "16");   // 경북 예천군

  private final String name;
  private final String areaCode;
  private final String sigunguCode;

  Region(String name, String areaCode, String sigunguCode) {
    this.name = name;
    this.areaCode = areaCode;
    this.sigunguCode = sigunguCode;
  }

  public String getName() {
    return name;
  }

  public String getAreaCode() {
    return areaCode;
  }

  public String getSigunguCode() {
    return sigunguCode;
  }

  public static Region fromCode(String code) {
    return Arrays.stream(values())
        .filter(r -> r.name().equalsIgnoreCase(code))
        .findFirst()
        .orElseThrow(() -> new ContentException(ErrorCode.CONTENT_INVALID_REGION));
  }
}
