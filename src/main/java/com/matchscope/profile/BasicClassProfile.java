package com.matchscope.profile;

import com.matchscope.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.Modifier;
import soot.SootClass;
import soot.SootField;
import soot.SootMethod;
import soot.tagkit.AnnotationTag;
import soot.tagkit.Tag;
import soot.tagkit.VisibilityAnnotationTag;
import soot.util.Chain;

import java.util.*;

public class BasicClassProfile {
    private final SootClass clazz;
    private final String name;
    private final Chain<SootField> sootFields;
    private boolean isEnum;
    private boolean isInterface;
    private String superClass;
    private List<String> interfaces;
    private final List<SootMethod> methodList;
    private final int methodNum;
    private final List<MethodProfile> methodProfiles;
    private final List<String> constantStrings = new ArrayList<>();
    private final List<String> methodFuzzyDescriptors = new ArrayList<>();
    private final List<String> methodLevel0Hashes = new ArrayList<>();
    private final List<String> methodLevel1Hashes = new ArrayList<>();
    private final List<String> methodLevel2Hashes = new ArrayList<>();
    private final List<String> methodLevel0FuzzyHashes = new ArrayList<>();
    private final List<String> methodLevel1FuzzyHashes = new ArrayList<>();
    private final List<String> methodLevel2FuzzyHashes = new ArrayList<>();
    private String level0Hash;
    private String level0FuzzyHash;
    private Enum<ClassType> classType;
    private int weight = 0;
    private final Logger logger = LoggerFactory.getLogger(BasicClassProfile.class);

    public BasicClassProfile(SootClass sootClass) {
        this.clazz = sootClass;
        this.name = sootClass.getName();
        this.sootFields = sootClass.getFields();
        this.methodList = new ArrayList<>(clazz.getMethods());
        this.methodProfiles = new ArrayList<>();

        constructDependencies();
        constructMethodProfiles();
        calculateHashes();

        // not same with the soot method num since we filtered synthetic methods
        this.methodNum = this.methodProfiles.size();
        this.weight = this.methodNum + this.sootFields.size();

    }

    private void constructDependencies() {
        this.isEnum = this.clazz.isEnum();
        this.isInterface = this.clazz.isInterface();
        if (this.isEnum) {
            this.classType = ClassType.ENUM;
        } else if (this.isInterface) {
            this.classType = ClassType.INTERFACE;
        } else {
            this.classType = ClassType.CLASS;
        }

        this.superClass = "";
        try {
            this.superClass = this.clazz.getSuperclass().getName();
        } catch (Exception ignored) {}

        this.interfaces = new ArrayList<>();
        for (SootClass sc: this.clazz.getInterfaces()) {
            this.interfaces.add(sc.getName());
            Collections.sort(this.interfaces);
        }

    }


    private void constructMethodProfiles() {
        for (SootMethod m: this.methodList) {
            if (m.getName().startsWith("access$") ||
                isDeprecated(m) || Modifier.isSynthetic(m.getModifiers())
            ) {
                continue;
            }
            MethodProfile p = new MethodProfile(m);
            this.methodProfiles.add(p);
        }

        for (MethodProfile mp: this.methodProfiles) {
            this.constantStrings.addAll(mp.getConstantStrings());
            this.methodLevel0Hashes.add(mp.getLevel0Hash());
            this.methodLevel1Hashes.add(mp.getLevel1Hash());
            this.methodLevel2Hashes.add(mp.getLevel2Hash());
            this.methodLevel0FuzzyHashes.add(mp.getLevel0FuzzyHash());
            this.methodLevel1FuzzyHashes.add(mp.getLevel1FuzzyHash());
            this.methodLevel2FuzzyHashes.add(mp.getLevel2FuzzyHash());
            this.methodFuzzyDescriptors.add(mp.getFuzzyDescriptor());
        }
        Collections.sort(this.constantStrings);
        Collections.sort(this.methodLevel0Hashes);
        Collections.sort(this.methodLevel1Hashes);
        Collections.sort(this.methodLevel2Hashes);
        Collections.sort(this.methodLevel0FuzzyHashes);
        Collections.sort(this.methodLevel1FuzzyHashes);
        Collections.sort(this.methodLevel2FuzzyHashes);
        Collections.sort(this.methodFuzzyDescriptors);
    }

    private void calculateHashes() {
        String featureString = getLevel0FeatureString();
//        logger.debug(featureString);
        this.level0Hash = Utils.calculateHash(featureString);
        this.level0FuzzyHash = Utils.calculateFuzzyHash(featureString);
    }

    public Set<String> getFieldsType() {
        Set<String> fieldsType = new TreeSet<>();
        Chain<SootField> fields = this.clazz.getFields();
        for (SootField field: fields) {
            fieldsType.add(Utils.getRawType(field.getType().toString()));
        }
        return fieldsType;
    }

    private boolean isDeprecated(SootMethod sootMethod) {
        List<Tag> tags = sootMethod.getTags();
        for (Tag tag: tags) {
            // VisibilityParameterAnnotationTag
            if (tag instanceof VisibilityAnnotationTag) {
                ArrayList<AnnotationTag> annotationTags = ((VisibilityAnnotationTag) tag).getAnnotations();
                for (AnnotationTag annotationTag: annotationTags) {
                    String type = annotationTag.getType();
                    if (Objects.equals(type, "Lkotlin/Deprecated;")) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public String getName() {
        return this.name;
    }

    public int getMethodNum() {
        return this.methodNum;
    }

    public List<SootMethod> getMethodList() {
        return this.methodList;
    }

    public List<MethodProfile> getMethodProfiles() {
        return methodProfiles;
    }

    public SootClass getSootClass() {
        return this.clazz;
    }

    public List<String> getConstantStrings() {
        return constantStrings;
    }

    private String getInstructions() {
        List<String> instructions = new ArrayList<>();
        for (MethodProfile mp: this.getMethodProfiles()) {
            List<String> instruction = mp.getInstructions();
            instructions.add(String.join(",", instruction));
        }
        Collections.sort(instructions);
        return String.join("_", instructions);
    }

    public String getLevel0FeatureString() {
        ArrayList<String> fieldList = new ArrayList<>();
        for (SootField sf: sootFields) {
            fieldList.add(Utils.getNormalizedType(sf.getType().toString()));
        }
        Collections.sort(fieldList);

        ArrayList<String> interfaceList = new ArrayList<>();
        for (String s: this.interfaces) {
            interfaceList.add(Utils.getNormalizedClassName(s));
        }
        Collections.sort(interfaceList);

        String fields = String.join("_", fieldList);
        String interfacesStr = String.join("_", interfaceList);
        String superClass = Utils.getNormalizedClassName(this.superClass);
        String methods = String.join("_", methodFuzzyDescriptors);
        String constantStrings = String.join("_", this.constantStrings);
        return fields + "__" + interfacesStr + "__" + superClass + "__" + methods + "__" + constantStrings;
    }

    public String getLevel0Hash() {
        return this.level0Hash;
    }

    public boolean isEnum() {
        return isEnum;
    }

    public boolean isInterface() {
        return isInterface;
    }

    public String getSuperClass() {
        return superClass;
    }

    public List<String> getInterfaces() {
        return interfaces;
    }

    public Enum<ClassType> getClassType() {
        return classType;
    }

    public String getLevel0FuzzyHash() {
        return level0FuzzyHash;
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    @Override
    public String toString() {
        return name;
    }
}
