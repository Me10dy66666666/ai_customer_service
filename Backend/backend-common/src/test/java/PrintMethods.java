import org.springframework.data.redis.serializer.RedisSerializer;
import java.lang.reflect.Method;

public class PrintMethods {
    public static void main(String[] args) {
        for (Method m : RedisSerializer.class.getMethods()) {
            System.out.println(m.getName());
        }
    }
}