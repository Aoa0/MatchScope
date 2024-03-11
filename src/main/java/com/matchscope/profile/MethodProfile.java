package com.matchscope.profile;

import com.matchscope.Utils;
import soot.*;
import soot.jimple.NumericConstant;
import soot.jimple.Stmt;
import soot.jimple.StringConstant;
import soot.jimple.internal.ConditionExprBox;
import soot.jimple.internal.ImmediateBox;

import java.util.*;

public class MethodProfile {
    private final SootMethod sootMethod;
    private final String name;
    private final String className;
    private final List<String> constantStrings;
    private String level0Hash;   // use method descriptor
    private String level1Hash;   // use invoked methods
    private String level2Hash;   // use instruction operation
    private String level0FuzzyHash;
    private String level1FuzzyHash;
    private String level2FuzzyHash;
    private final String returnType;
    private final List<String> parameterTypes;
    private int statementNum = 0;
    private final List<String> instructions;
    private Body body = null;
    private final List<String> invokedMethods;

    public MethodProfile(SootMethod sootMethod) {
        this.sootMethod = sootMethod;
        this.name = sootMethod.getName();
        this.className = sootMethod.getDeclaringClass().getName();
        this.returnType = Utils.getRawType(this.sootMethod.getReturnType().toString());
        this.parameterTypes = new ArrayList<>();
        List<Type> pts = sootMethod.getParameterTypes();
        for (Type parameterType : pts) {
            this.parameterTypes.add(Utils.getRawType(parameterType.toString()));
        }

        try {
            body = sootMethod.retrieveActiveBody();
        } catch (Exception ignore) {}

        this.instructions = new ArrayList<>();
        this.constantStrings = new ArrayList<>();
        this.invokedMethods = new ArrayList<>();

        constructMethodProfiles();
        calculateMethodHashes();

    }

    private void calculateMethodHashes() {
        String fuzzyDescriptor = getFuzzyDescriptor();
        this.level0Hash = Utils.calculateHash(fuzzyDescriptor);
        this.level0FuzzyHash = Utils.calculateFuzzyHash(fuzzyDescriptor);

        String normalizedInvokedMethods = getNormalizedInvokedMethods();
        this.level1Hash = Utils.calculateHash(normalizedInvokedMethods);
        this.level1FuzzyHash = Utils.calculateFuzzyHash(normalizedInvokedMethods);

        String instructions = String.join(" ", this.instructions);
        this.level2Hash = Utils.calculateHash(instructions);
        this.level2FuzzyHash = Utils.calculateFuzzyHash(instructions);

    }


    private String getNormalizedInvokedMethods() {
        List<String> normalized = new ArrayList<>();
        for (String i: this.invokedMethods) {
            normalized.add(Utils.getNormalizedClassName(i));
        }
        Collections.sort(normalized);
        return String.join(" ", normalized);
    }

    public String getFuzzyDescriptor() {
        // Class Name + Access Flag + return Type + Method Name + Parameter Types
        StringBuilder fuzzyDescriptor = new StringBuilder("(");
        List<String> parameters = new ArrayList<>();
        for (String s : parameterTypes) {
            if (Utils.isAndroidType(s)) {
                parameters.add(s);
            } else {
                parameters.add("X");
            }
        }

        Collections.sort(parameters);
        String parameterString = String.join("," ,parameters);
        fuzzyDescriptor.append(parameterString);
        fuzzyDescriptor.append(")");

        Type type = sootMethod.getReturnType();
        if (Utils.isAndroidType(type.toString())) {
            fuzzyDescriptor.append(type);
        } else {
            fuzzyDescriptor.append("X");
        }
//        System.out.println(method.getName() + " " + method.getParameterTypes() + " " + method.getBytecodeParms() + " " + fuzzyDescriptor);
        return fuzzyDescriptor.toString();
    }

    private void constructMethodProfiles() {
        if (this.body != null) {
            this.statementNum = body.getUnits().size();
//            System.out.println(this.sootMethod.getSignature());
            for (Unit unit: body.getUnits()) {
//                System.out.println(unit + " " + unit.getClass());
//                for (ValueBox b: unit.getUseAndDefBoxes()) {
//                    System.out.println(b + " " + b.getClass());
//                }
//                System.out.println();
                String classname = unit.getClass().toString();
                String name = classname.substring(classname.lastIndexOf('.') + 1);
                String operator = name.substring(1, name.length() - 4);
                List<String> values = new ArrayList<>();

                if (unit instanceof Stmt) {
                    if (((Stmt) unit).containsInvokeExpr()) {
                        SootMethod m = ((Stmt) unit).getInvokeExpr().getMethod();
                        SootClass c = m.getDeclaringClass();
                        this.invokedMethods.add(c + " " + m.getSubSignature());
                        if (Utils.isAndroidClass(c)) {
                            values.add(c.toString());
                        }
                    }
                }

                for(ValueBox b: unit.getUseBoxes()) {
                    if (b instanceof ImmediateBox) {
                        Value value = b.getValue();
                        if (value instanceof StringConstant) {
                            String cs = value.toString();
                            cs = cs.substring(1, cs.length() - 1);
                            if (cs.equals("Null throw statement replaced by Soot")) {
                                cs = "NULL";
                            }
                            constantStrings.add(cs);
                            values.add(cs);
                        } else if (value instanceof NumericConstant) {
                            values.add(value.toString());
                        }
                    } else if (b instanceof ConditionExprBox) {
                        values.add(b.getValue().toString().split(" ")[1]);
                    }
                }
                Collections.sort(values);
                String instruction = operator + " " + String.join(" ", values);
                instructions.add(instruction);
            }
        }

    }

    public List<String> getInstructions() {
        return instructions;
    }

    public List<String> getConstantStrings() {
        return constantStrings;
    }

    public String getLevel0Hash() {
        return level0Hash;
    }

    public String getLevel1Hash() {
        return level1Hash;
    }

    public String getLevel2Hash() {
        return level2Hash;
    }

    public int getStatementNum() {
        return statementNum;
    }

    public SootMethod getSootMethod() {
        return sootMethod;
    }

    public String getReturnType() {
        return returnType;
    }

    public List<String> getParameterTypes() {
        return parameterTypes;
    }

    public List<String> getInvokedMethods() {
        return invokedMethods;
    }

    public List<String> getInvokedNonSystemMethod() {
        // ToDo: handle overload that multiple function share the same name
        List<String> ret = new ArrayList<>();
        for (String s: invokedMethods) {
            if (!Utils.isAndroidClass(s)) {
                ret.add(s);
            }
        }
        return ret;
    }

    public String getName() {
        return name;
    }

    public String getClassName() {
        return className;
    }

    public String getLevel0FuzzyHash() {
        return level0FuzzyHash;
    }

    public String getLevel1FuzzyHash() {
        return level1FuzzyHash;
    }

    public String getLevel2FuzzyHash() {
        return level2FuzzyHash;
    }
}
