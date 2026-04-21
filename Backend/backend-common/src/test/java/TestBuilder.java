import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;

public class TestBuilder {
    public static void main(String[] args) {
        boolean classDep = GenericJackson2JsonRedisSerializer.class.isAnnotationPresent(Deprecated.class);
        System.out.println("Class Deprecated: " + classDep);
    }
}