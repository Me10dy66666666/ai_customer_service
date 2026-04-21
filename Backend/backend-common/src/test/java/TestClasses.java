import org.springframework.data.redis.serializer.*;

public class TestClasses {
    public static void main(String[] args) {
        System.out.println("Jackson2JsonRedisSerializer dep: " + Jackson2JsonRedisSerializer.class.isAnnotationPresent(Deprecated.class));
        System.out.println("GenericJackson2JsonRedisSerializer dep: " + GenericJackson2JsonRedisSerializer.class.isAnnotationPresent(Deprecated.class));
    }
}