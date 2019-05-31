package nl.knmi.adaguc.services.esgfsearch.threddscatalog;

import nl.knmi.adaguc.tools.MyXMLParser;
import nl.knmi.adaguc.tools.Tuple;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.Collection;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class Parser {
    public JSONArray parseVariables(MyXMLParser.XMLElement dataset) throws JSONException {
        //Put variable info
        JSONArray variableInfos = new JSONArray();
        if (dataset == null) return variableInfos;

        Collection<MyXMLParser.XMLElement> variables = null;
        try {
            variables = dataset.get("variables").getList("variable");
        } catch (Exception ignored) { }

        if (variables == null) return variableInfos;

        for (MyXMLParser.XMLElement variable : variables) {
            JSONObject varInfo = parseVariable(variable);
            variableInfos.put(varInfo);
        }

        return variableInfos;
    }

    public JSONObject parseVariable(MyXMLParser.XMLElement variable) {
        JSONObject varInfo = new JSONObject();
        try {
            varInfo.put("name", variable.getAttrValue("name"));
            varInfo.put("vocabulary_name", variable.getAttrValue("vocabulary_name"));
            varInfo.put("long_name", variable.getValue());

        } catch (Exception ignored) { }

        return varInfo;
    }

    public static class DataSize {
        public final char Letter;
        public final String Title;
        public final int Magnitude;

        private DataSize(char letter, String title, int magnitude) {
            Letter = letter;
            Title = title;
            Magnitude = magnitude;
        }
    }

    public enum DataSizes {
        PetaBytes(new DataSize('P', "PetaBytes", 5)),
        TeraBytes(new DataSize('T', "TeraBytes", 4)),
        GigaBytes(new DataSize('G', "GigaBytes", 3)),
        MegaBytes(new DataSize('M', "MegaBytes", 2)),
        KiloBytes(new DataSize('K', "KiloBytes", 1)),
        Bytes(new DataSize('B', "Bytes", 0));

        private final DataSize object;

        static DataSize byPredicate(Predicate<DataSize> predicate) {
            Stream<DataSizes> stream = Arrays.stream(DataSizes.values());
            Stream<DataSize> dataSizes = stream.map(entry -> entry.object);

            return dataSizes.filter(predicate)
                            .findFirst()
                            .orElse(null);
        }

        static DataSize byLetter(char letter) {
            return byPredicate(dataSize -> dataSize.Letter == letter);
        }

        DataSizes(DataSize object) { this.object = object; }
    }

    public Tuple<String, Integer> parseDataSize(String units) {

        Predicate<String> isValidDataSizeFormat = (test) -> test.endsWith("bytes") && test.length() == 6;
        Predicate<Character> isRegisteredDataSize = (possibleDataSize) -> DataSizes.byLetter(possibleDataSize) != null;

        if (!isValidDataSizeFormat.test(units) || !isRegisteredDataSize.test(units.charAt(0))) {
            return new Tuple<>(units, 0);
        }

        DataSize size = DataSizes.byLetter(units.charAt(0));

        int power = size.Magnitude;
        units = String.valueOf(size.Letter);
        return new Tuple<>(units, power);
    }
}
