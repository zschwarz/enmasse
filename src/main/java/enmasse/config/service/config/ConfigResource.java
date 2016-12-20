package enmasse.config.service.config;

import enmasse.config.service.model.Resource;
import io.fabric8.kubernetes.api.model.HasMetadata;

import java.util.Map;

/**
 * Extends a Kubernetes resource with metadata and applies hashCode and equals
 */
public class ConfigResource extends Resource<HasMetadata> {
    private final String kind;
    private final String name;
    private final Map<String, String> labels;

    public ConfigResource(HasMetadata resource) {
        this.kind = resource.getKind();
        this.name = resource.getMetadata().getName();
        this.labels = resource.getMetadata().getLabels();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ConfigResource that = (ConfigResource) o;

        if (!kind.equals(that.kind)) return false;
        if (!name.equals(that.name)) return false;
        return labels != null ? labels.equals(that.labels) : that.labels == null;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getKind() {
        return kind;
    }

    @Override
    public int hashCode() {
        int result = kind.hashCode();
        result = 31 * result + name.hashCode();
        result = 31 * result + (labels != null ? labels.hashCode() : 0);
        return result;
    }

    public Map<String, String> getLabels() {
        return labels;
    }

    @Override
    public String toString() {
        return kind + ":" + name;
    }
}
