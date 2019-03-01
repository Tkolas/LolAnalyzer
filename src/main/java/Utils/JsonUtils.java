package Utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.json.JSONArray;

import java.io.IOException;
import java.util.List;

public class JsonUtils {

    @SuppressWarnings("UnnecessaryLocalVariable")
    public static <T> List<T> JSONArrayToList(ObjectMapper mapper, JSONArray array, Class<T> listType) throws IOException {
        List<T> list =
                mapper.readValue(
                        array.toString(),
                        mapper.getTypeFactory().constructCollectionType(List.class, listType)
                );
        return list;
    }
}
