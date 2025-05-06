package com.razoris.bootweb.utils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 엔티티, 맵 리스트, JSON 간 데이터 전환을 지원하는 유틸리티 클래스.
 *
 * <p>주요 기능:</p>
 * <ul>
 *   <li>{@code List<Map<String, Object>>} ↔ 전치 및 복원</li>
 *   <li>{@code List<T>} (엔티티) ↔ 전치 및 복원</li>
 *   <li>객체 ↔ JSON 변환</li>
 *   <li>객체 ↔ Map 변환</li>
 * </ul>
 */
public class TransposeHelper {

    private static final ObjectMapper mapper = new ObjectMapper();

    /**
     * Map 리스트 데이터를 전치.
     *
     * @param data List<Map<String, Object>> 형식의 테이블형 데이터
     * @return Map<String, List < Object>> 형식으로 전치된 데이터
     */
    public static Map<String, List<Object>> transposeMapList(List<Map<String, Object>> data) {
        Map<String, List<Object>> result = new LinkedHashMap<>();
        for (Map<String, Object> row : data) {
            for (Map.Entry<String, Object> entry : row.entrySet()) {
                result.computeIfAbsent(entry.getKey(), k -> new ArrayList<>())
                        .add(entry.getValue());
            }
        }
        return result;
    }

    /**
     * 전치된 Map 데이터를 다시 리스트 형태로 복원.
     *
     * @param data Map<String, List<Object>> 형식의 전치된 데이터
     * @return 원래의 List<Map<String, Object>> 형태로 복원된 결과
     */
    public static List<Map<String, Object>> untransposeToMapList(Map<String, List<Object>> data) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (data == null || data.isEmpty()) return result;

        int rowCount = data.values().iterator().next().size();
        for (int i = 0; i < rowCount; i++) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (Map.Entry<String, List<Object>> entry : data.entrySet()) {
                row.put(entry.getKey(), i < entry.getValue().size() ? entry.getValue().get(i) : null);
            }
            result.add(row);
        }
        return result;
    }

    /**
     * 엔티티 리스트를 전치하여 컬럼 기준의 Map 형태로 변환.
     *
     * @param entities 엔티티 리스트
     * @param clazz    엔티티 클래스 타입
     * @param <T>      엔티티 제네릭 타입
     * @return Map<String, List < Object>> 형식으로 전치된 데이터
     */
    public static <T> Map<String, List<Object>> transposeEntities(List<T> entities, Class<T> clazz) {
        Map<String, List<Object>> result = new LinkedHashMap<>();
        if (entities == null || entities.isEmpty()) return result;

        for (Method method : clazz.getMethods()) {
            if (isGetter(method)) {
                String fieldName = getFieldName(method.getName());
                List<Object> values = new ArrayList<>();
                for (T entity : entities) {
                    try {
                        values.add(method.invoke(entity));
                    } catch (Exception e) {
                        values.add(null);
                    }
                }
                result.put(fieldName, values);
            }
        }
        return result;
    }

    /**
     * 전치된 Map 데이터를 엔티티 리스트로 복원.
     *
     * @param data  전치된 Map 데이터
     * @param clazz 엔티티 클래스 타입
     * @param <T>   엔티티 제네릭 타입
     * @return List<T> 형식으로 복원된 엔티티 리스트
     */
    public static <T> List<T> untransposeToEntities(Map<String, List<Object>> data, Class<T> clazz) {
        List<T> result = new ArrayList<>();
        if (data == null || data.isEmpty()) return result;

        int rowCount = data.values().iterator().next().size();
        for (int i = 0; i < rowCount; i++) {
            try {
                T instance = clazz.getDeclaredConstructor().newInstance();
                for (Map.Entry<String, List<Object>> entry : data.entrySet()) {
                    Field field = clazz.getDeclaredField(entry.getKey());
                    field.setAccessible(true);
                    Object value = i < entry.getValue().size() ? entry.getValue().get(i) : null;
                    field.set(instance, value);
                }
                result.add(instance);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return result;
    }

    /**
     * 객체를 JSON 문자열로 직렬화.
     *
     * @param obj 직렬화할 객체
     * @return JSON 문자열
     */
    public static String toJson(Object obj) {
        try {
            return mapper.writeValueAsString(obj);
        } catch (Exception e) {
            throw new RuntimeException("JSON 직렬화 실패", e);
        }
    }

    /**
     * JSON 문자열을 객체로 역직렬화.
     *
     * @param json  JSON 문자열
     * @param clazz 변환할 클래스 타입
     * @param <T>   객체 타입
     * @return 역직렬화된 객체
     */
    public static <T> T fromJson(String json, Class<T> clazz) {
        try {
            return mapper.readValue(json, clazz);
        } catch (Exception e) {
            throw new RuntimeException("JSON 역직렬화 실패", e);
        }
    }

    /**
     * 객체를 Map<String, Object>로 변환.
     *
     * @param obj 변환할 객체
     * @return 필드 기반의 맵
     */
    public static Map<String, Object> toMap(Object obj) {
        return mapper.convertValue(obj, new TypeReference<Map<String, Object>>() {
        });
    }

    /**
     * Map<String, Object>를 객체로 변환.
     *
     * @param map   변환할 맵
     * @param clazz 대상 클래스 타입
     * @param <T>   객체 타입
     * @return 변환된 객체
     */
    public static <T> T fromMap(Map<String, Object> map, Class<T> clazz) {
        return mapper.convertValue(map, clazz);
    }

    // 내부 유틸: getter 판단
    private static boolean isGetter(Method method) {
        return method.getName().startsWith("get")
                && method.getParameterCount() == 0
                && !void.class.equals(method.getReturnType());
    }

    // 내부 유틸: getter → 필드 이름
    private static String getFieldName(String getterName) {
        String raw = getterName.substring(3);
        return raw.substring(0, 1).toLowerCase() + raw.substring(1);
    }
}
