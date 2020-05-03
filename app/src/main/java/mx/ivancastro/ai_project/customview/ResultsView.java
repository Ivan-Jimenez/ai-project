package mx.ivancastro.ai_project.customview;

import java.util.List;

import mx.ivancastro.ai_project.tflite.Classifier;

public interface ResultsView {
    public void setResults(final List<Classifier.Recognition> results);
}
