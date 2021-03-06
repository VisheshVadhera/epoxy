package com.airbnb.epoxy;

import com.squareup.javapoet.ClassName;
import com.sun.source.util.Trees;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symbol.VarSymbol;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeScanner;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.MirroredTypeException;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

/**
 * Scans R files and and compiles layout resource values in those R classes. This allows us to look
 * up raw layout resource values (eg 23523452) and convert that to the resource name (eg
 * R.layout.my_view) so that we can properly reference that resource. This is important in library
 * projects where the R value at process time can be different from the final R value in the app.
 * <p>
 * This is adapted from Butterknife. https://github.com/JakeWharton/butterknife/pull/613
 */
class LayoutResourceProcessor {
  private static final String CLASS_ANNOTATION_CANONICAL_NAME =
      EpoxyModelClass.class.getCanonicalName();

  private final ErrorLogger errorLogger;
  private final Elements elementUtils;
  private final Types typeUtils;
  /**
   * Map of fully qualified model name to layout resource for the EpoxyModelClass annotation on that
   * model.
   */
  private final Map<String, ModelLayoutResource> modelLayoutMap = new HashMap<>();
  private Trees trees;
  private final Map<String, ClassName> rClassNameMap = new HashMap<>();

  LayoutResourceProcessor(ProcessingEnvironment processingEnv, ErrorLogger errorLogger,
      Elements elementUtils, Types typeUtils) {
    this.errorLogger = errorLogger;
    this.elementUtils = elementUtils;
    this.typeUtils = typeUtils;

    try {
      trees = Trees.instance(processingEnv);
    } catch (IllegalArgumentException ignored) {

    }
  }

  /**
   * Looks up the layout resource used in the {@link EpoxyModelClass} annotation on this model. The
   * given element is expected to be an EpoxyModel that is annotated with {@link EpoxyModelClass}
   */
  ModelLayoutResource getLayoutForModel(TypeElement modelClassElement) {
    String modelName = getModelNameFromElement(modelClassElement);
    ModelLayoutResource modelLayout = modelLayoutMap.get(modelName);

    if (modelLayout == null) {
      // If a value was hardcoded, instead of an R.layout value, then there won't be a match.
      // We just use the hardcoded value directly in this case.
      EpoxyModelClass annotation = modelClassElement.getAnnotation(EpoxyModelClass.class);
      modelLayout = new ModelLayoutResource(annotation.layout());
      modelLayoutMap.put(modelName, modelLayout);
    }

    return modelLayout;
  }

  private String getModelNameFromElement(TypeElement modelClassElement) {
    return modelClassElement.getQualifiedName().toString();
  }

  /**
   * Processes details about the layout resources used in {@link EpoxyModelClass} annotations
   * across all Epoxy models. Details for each model layout is stored in a map so they can be looked
   * up with the model name via {@link #getLayoutForModel(TypeElement)}
   */
  void processResources(RoundEnvironment env) {
    modelLayoutMap.clear();

    if (trees == null) {
      return;
    }

    ModelLayoutScanner scanner = new ModelLayoutScanner();

    for (Element modelElement : env.getElementsAnnotatedWith(EpoxyModelClass.class)) {
      EpoxyModelClass modelAnnotation = modelElement.getAnnotation(EpoxyModelClass.class);
      int layoutValue = modelAnnotation.layout();

      if (layoutValue == 0) {
        // A layout value was not provided in this annotation
        continue;
      }

      JCTree tree = (JCTree) trees.getTree(modelElement, getAnnotationMirror(modelElement));
      if (tree == null) {
        // tree can be null if the references are compiled types and not source
        continue;
      }

      // Collects details about the layout resource used for the annotation parameter
      tree.accept(scanner);
      ScannerResult result = scanner.getResult();
      if (result == null) {
        // Unable to get details on layout resource. This could happen if a layout value is
        // hardcoded
        continue;
      }



      if (layoutValue != result.resourceValue) {
        // I don't know why this would happen, but it seems worth sanity checking for
        errorLogger.logError(
            "Layout resource from scanner did not match expected value. Class: %s Expected: %s "
                + "Scanner Value: %s",
            modelElement.getSimpleName(), layoutValue, result.resourceValue);
        continue;
      }

      ModelLayoutResource layoutResource =
          new ModelLayoutResource(
              getClassName(result.rClass),
              result.resourceName,
              result.resourceValue
          );

      String modelName = getModelNameFromElement((TypeElement) modelElement);
      modelLayoutMap.put(modelName, layoutResource);
    }
  }

  private AnnotationMirror getAnnotationMirror(Element element) {
    for (AnnotationMirror annotationMirror : element.getAnnotationMirrors()) {
      if (annotationMirror.getAnnotationType().toString().equals(CLASS_ANNOTATION_CANONICAL_NAME)) {
        return annotationMirror;
      }
    }

    errorLogger.logError("Unable to get %s annotation on model %",
        EpoxyModelClass.class, element.getSimpleName());

    return null;
  }

  /**
   * Scans the EpoxyModelClass annotation to examine the layout resource parameter. It finds the R
   * class that the layout resource belongs to, as well as the resource name and value.
   */
  private class ModelLayoutScanner extends TreeScanner {
    private ScannerResult result;

    ScannerResult getResult() {
      return result;
    }

    @Override
    public void visitSelect(JCTree.JCFieldAccess jcFieldAccess) {
      // This is the layout resource parameter inside the EpoxyModelClass annotation
      Symbol symbol = jcFieldAccess.sym;

      if (symbol instanceof VarSymbol
          && symbol.getEnclosingElement() != null // The R.layout class
          && symbol.getEnclosingElement().getEnclosingElement() != null // The R class
          && symbol.getEnclosingElement().getEnclosingElement().enclClass() != null) {

        result = parseResourceSymbol((VarSymbol) symbol);
      } else {
        result = null;
      }
    }

    private ScannerResult parseResourceSymbol(VarSymbol symbol) {
      // eg com.airbnb.epoxy.R
      String rClass = symbol.getEnclosingElement().getEnclosingElement().enclClass().className();

      // eg com.airbnb.epoxy.R.layout
      String layoutClass = symbol.getEnclosingElement().getQualifiedName().toString();

      // Make sure this is a layout resource
      if (!(rClass + ".layout").equals(layoutClass)) {
        errorLogger.logError("%s annotation requires a layout resource but received %s",
            EpoxyModelClass.class, layoutClass);
        return null;
      }

      // eg button_layout, as in R.layout.button_layout
      String layoutResourceName = symbol.getSimpleName().toString();

      Object layoutValue = symbol.getConstantValue();
      if (!(layoutValue instanceof Integer)) {
        errorLogger.logError("%s annotation requires an int value but received %s",
            EpoxyModelClass.class, symbol.getQualifiedName());
        return null;
      }

      return new ScannerResult(rClass, layoutResourceName, (int) layoutValue);
    }
  }

  private static class ScannerResult {
    final String rClass;
    final String resourceName;
    final int resourceValue;

    private ScannerResult(String rClass, String resourceName, int resourceValue) {
      this.rClass = rClass;
      this.resourceName = resourceName;
      this.resourceValue = resourceValue;
    }
  }

  /**
   * Builds a JavaPoet ClassName from the string value of an R class. This is memoized since there
   * should be very few different R classes used.
   */
  private ClassName getClassName(String rClass) {
    ClassName className = rClassNameMap.get(rClass);

    if (className == null) {
      Element rClassElement;
      try {
        rClassElement = elementUtils.getTypeElement(rClass);
      } catch (MirroredTypeException mte) {
        rClassElement = typeUtils.asElement(mte.getTypeMirror());
      }

      String rClassPackageName =
          elementUtils.getPackageOf(rClassElement).getQualifiedName().toString();
      className = ClassName.get(rClassPackageName, "R", "layout");

      rClassNameMap.put(rClass, className);
    }

    return className;
  }
}
