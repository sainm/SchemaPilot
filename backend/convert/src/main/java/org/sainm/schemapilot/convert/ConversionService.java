package org.sainm.schemapilot.convert;

import java.util.ArrayList;
import java.util.List;

import org.sainm.schemapilot.model.DbObject;

public class ConversionService {

    private final ObjectConverter objectConverter;
    private final ConversionRepository conversionRepository;

    public ConversionService(ObjectConverter objectConverter, ConversionRepository conversionRepository) {
        this.objectConverter = objectConverter;
        this.conversionRepository = conversionRepository;
    }

    public List<ConversionResult> convertObjects(List<DbObject> objects) {
        List<ConversionResult> results = new ArrayList<>();
        for (DbObject object : objects) {
            results.add(objectConverter.convert(object, new ConversionContext(
                    object.projectId(),
                    object.sourceProjectId(),
                    object.inputSourceId())));
        }
        if (!objects.isEmpty()) {
            conversionRepository.replaceInputSourceConversions(objects.getFirst().inputSourceId(), results);
        }
        return List.copyOf(results);
    }
}
