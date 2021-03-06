/*
 * Copyright 2014 The Closure Compiler Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.javascript.jscomp.newtypes;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * This class contains state that stays the same throughout a single type check,
 * but can vary across different compilations.
 * 1) It contains the built-in types, which we initialize when we read their
 *    definitions in externs.
 * 2) It knows whether we are in compatibility mode (looser checks in the style
 *    of the old type checker).
 *
 * Built-in nominal types (Function, Object, Array, String, etc.) must be set
 * explicitly in externs, and are set with the corresponding setter methods
 * as they are crawled. They will remain null if not defined anywhere.
 *
 * There should only be one instance of this class per Compiler object.
 *
 * @author blickly@google.com (Ben Lickly)
 * @author dimvar@google.com (Dimitris Vardoulakis)
 */
public final class JSTypes {
  // The types that are final don't depend on the externs. The types that
  // are non-final, such as numberInstance, are filled in when we traverse
  // the externs during GlobalTypeInfo.
  public final JSType BOOLEAN;
  public final JSType BOTTOM;
  public final JSType FALSE_TYPE;
  public final JSType FALSY;
  public final JSType NULL;
  public final JSType NUMBER;
  public final JSType STRING;
  public final JSType TOP;
  public final JSType TOP_SCALAR;
  public final JSType TRUE_TYPE;
  public final JSType TRUTHY;
  public final JSType UNDEFINED;
  public final JSType UNKNOWN;

  // Currently, TOP_OBJECTTYPE has two conflicting roles: the supertype of all
  // object types, and the type of an empty object literal.
  // In particular, its kind is UNRESTRICTED, which is confusing, because this
  // kind is a subkind of STRUCT and DICT.
  // We take that into account in ObjectType#specialize, but not yet in meet and join.
  // TODO(dimvar): Find a clean way to split the two types & avoid the confusion
  final ObjectType TOP_OBJECTTYPE;
  final PersistentMap<String, Property> BOTTOM_PROPERTY_MAP;
  final ObjectType BOTTOM_OBJECT;
  public final JSType TOP_OBJECT;
  public final JSType TOP_STRUCT;
  public final JSType TOP_DICT;

  // Corresponds to Function, which is a subtype and supertype of all functions.
  final FunctionType QMARK_FUNCTION;
  final FunctionType BOTTOM_FUNCTION;
  // Theoretically, the top function takes an infinite number of required
  // arguments of type BOTTOM and returns TOP. If this function is ever called,
  // it's a type error. Despite that, we want to represent it and not go
  // directly to JSType.TOP, to avoid spurious warnings.
  // Eg, after an IF, we may see a type (number | top_function); this type could
  // get specialized to number and used legitimately.
  // We can't represent the theoretical top function, so we special-case
  // TOP_FUNCTION below. However, the outcome is the same; if our top function
  // is ever called, a warning is inevitable.
  final FunctionType TOP_FUNCTION;
  final FunctionType LOOSE_TOP_FUNCTION;

  final Map<String, JSType> MAP_TO_UNKNOWN;

  // Commonly-used types. We create them once here and reuse them
  public final JSType NUMBER_OR_STRING;
  final JSType UNDEFINED_OR_BOOLEAN;
  final JSType UNDEFINED_OR_NUMBER;
  final JSType UNDEFINED_OR_STRING;
  public final JSType NULL_OR_UNDEFINED;
  final JSType NULL_OR_BOOLEAN;
  final JSType NULL_OR_NUMBER;
  final JSType NULL_OR_STRING;

  // Instances of Boolean, Number and String; used for auto-boxing scalars.
  private JSType numberInstance;
  private JSType booleanInstance;
  private JSType stringInstance;

  private ObjectType numberInstanceObjtype;
  private ObjectType booleanInstanceObjtype;
  private ObjectType stringInstanceObjtype;

  private JSType numberOrNumber;
  private JSType stringOrString;
  private JSType anyNumOrStr;

  private JSType regexpInstance;
  private RawNominalType arrayType;
  private RawNominalType builtinObject;
  private RawNominalType builtinFunction;
  private RawNominalType arguments;
  private RawNominalType iObject;

  final boolean allowMethodsAsFunctions;
  final boolean looseSubtypingForLooseObjects;
  final boolean bivariantArrayGenerics;

  private JSTypes(boolean inCompatibilityMode) {
    Map<String, JSType> types = JSType.createScalars(this);
    this.BOOLEAN = Preconditions.checkNotNull(types.get("BOOLEAN"));
    this.BOTTOM = Preconditions.checkNotNull(types.get("BOTTOM"));
    this.FALSE_TYPE = Preconditions.checkNotNull(types.get("FALSE_TYPE"));
    this.FALSY = Preconditions.checkNotNull(types.get("FALSY"));
    this.NULL = Preconditions.checkNotNull(types.get("NULL"));
    this.NUMBER = Preconditions.checkNotNull(types.get("NUMBER"));
    this.STRING = Preconditions.checkNotNull(types.get("STRING"));
    this.TOP = Preconditions.checkNotNull(types.get("TOP"));
    this.TOP_SCALAR = Preconditions.checkNotNull(types.get("TOP_SCALAR"));
    this.TRUE_TYPE = Preconditions.checkNotNull(types.get("TRUE_TYPE"));
    this.TRUTHY = Preconditions.checkNotNull(types.get("TRUTHY"));
    this.UNDEFINED = Preconditions.checkNotNull(types.get("UNDEFINED"));
    this.UNKNOWN = Preconditions.checkNotNull(types.get("UNKNOWN"));

    this.UNDEFINED_OR_BOOLEAN = Preconditions.checkNotNull(types.get("UNDEFINED_OR_BOOLEAN"));
    this.UNDEFINED_OR_NUMBER = Preconditions.checkNotNull(types.get("UNDEFINED_OR_NUMBER"));
    this.UNDEFINED_OR_STRING = Preconditions.checkNotNull(types.get("UNDEFINED_OR_STRING"));
    this.NULL_OR_BOOLEAN = Preconditions.checkNotNull(types.get("NULL_OR_BOOLEAN"));
    this.NULL_OR_NUMBER = Preconditions.checkNotNull(types.get("NULL_OR_NUMBER"));
    this.NULL_OR_STRING = Preconditions.checkNotNull(types.get("NULL_OR_STRING"));
    this.NULL_OR_UNDEFINED = Preconditions.checkNotNull(types.get("NULL_OR_UNDEFINED"));
    this.NUMBER_OR_STRING = Preconditions.checkNotNull(types.get("NUMBER_OR_STRING"));

    Map<String, FunctionType> functions = FunctionType.createInitialFunctionTypes(this);
    this.QMARK_FUNCTION = Preconditions.checkNotNull(functions.get("QMARK_FUNCTION"));
    this.BOTTOM_FUNCTION = Preconditions.checkNotNull(functions.get("BOTTOM_FUNCTION"));
    this.TOP_FUNCTION = Preconditions.checkNotNull(functions.get("TOP_FUNCTION"));
    this.LOOSE_TOP_FUNCTION = Preconditions.checkNotNull(functions.get("LOOSE_TOP_FUNCTION"));

    this.BOTTOM_PROPERTY_MAP = PersistentMap.of("_", Property.make(this.BOTTOM, this.BOTTOM));
    Map<String, ObjectType> objects = ObjectType.createInitialObjectTypes(this);
    this.TOP_OBJECTTYPE = Preconditions.checkNotNull(objects.get("TOP_OBJECTTYPE"));
    this.TOP_OBJECT = JSType.fromObjectType(this.TOP_OBJECTTYPE);
    this.TOP_STRUCT = JSType.fromObjectType(
        Preconditions.checkNotNull(objects.get("TOP_STRUCT")));
    this.TOP_DICT = JSType.fromObjectType(
        Preconditions.checkNotNull(objects.get("TOP_DICT")));
    this.BOTTOM_OBJECT = Preconditions.checkNotNull(objects.get("BOTTOM_OBJECT"));

    JSType.initObjects(this);

    this.allowMethodsAsFunctions = inCompatibilityMode;
    this.looseSubtypingForLooseObjects = inCompatibilityMode;
    this.bivariantArrayGenerics = inCompatibilityMode;

    this.MAP_TO_UNKNOWN = new Map<String, JSType>() {
      @Override
      public void clear() {
        throw new UnsupportedOperationException();
      }

      @Override
      public boolean containsKey(Object k) {
        return true;
      }

      @Override
      public boolean containsValue(Object v) {
        return v == JSType.UNKNOWN;
      }

      @Override
      public Set<Map.Entry<String, JSType>> entrySet() {
        throw new UnsupportedOperationException();
      }

      @Override
      public JSType get(Object k) {
        return JSType.UNKNOWN;
      }

      @Override
      public boolean isEmpty() {
        return false;
      }

      @Override
      public Set<String> keySet() {
        throw new UnsupportedOperationException();
      }

      @Override
      public JSType put(String k, JSType v) {
        throw new UnsupportedOperationException();
      }

      @Override
      public void putAll(Map<? extends String, ? extends JSType> m) {
        throw new UnsupportedOperationException();
      }

      @Override
      public JSType remove(Object k) {
        throw new UnsupportedOperationException();
      }

      @Override
      public int size() {
        throw new UnsupportedOperationException();
      }

      @Override
      public Collection<JSType> values() {
        return ImmutableSet.of(JSType.UNKNOWN);
      }

      @Override
      public int hashCode() {
        throw new UnsupportedOperationException();
      }

      @Override
      public boolean equals(Object o) {
        return o == this;
      }
    };
  }

  public static JSTypes init(boolean inCompatibilityMode) {
    return new JSTypes(inCompatibilityMode);
  }

  public JSType fromFunctionType(FunctionType fn) {
    return JSType.fromFunctionType(fn, getFunctionType());
  }

  public NominalType getFunctionType() {
    if (builtinFunction == null) {
      return null;
    }
    return builtinFunction.getAsNominalType();
  }

  public JSType looseTopFunction() {
    return topFunction().withLoose();
  }

  public JSType topFunction() {
    return fromFunctionType(this.TOP_FUNCTION);
  }

  // Corresponds to Function, which is a subtype and supertype of all functions.
  public JSType qmarkFunction() {
    return fromFunctionType(this.QMARK_FUNCTION);
  }

  public JSType getArrayInstance() {
    return getArrayInstance(this.UNKNOWN);
  }

  public NominalType getObjectType() {
    return this.builtinObject == null ? null : this.builtinObject.getAsNominalType();
  }

  public JSType getObjectInstance() {
    return this.builtinObject == null ? null : this.builtinObject.getInstanceAsJSType();
  }

  public NominalType getIObjectType() {
    return this.iObject == null ? null : this.iObject.getAsNominalType();
  }

  public JSType getArrayInstance(JSType t) {
    if (arrayType == null) {
      return this.UNKNOWN;
    }
    ImmutableList<String> typeParams = arrayType.getTypeParameters();
    JSType result = arrayType.getInstanceAsJSType();
    // typeParams can be != 1 in old externs files :-S
    if (typeParams.size() == 1) {
      String typeParam = Iterables.getOnlyElement(typeParams);
      result = result.substituteGenerics(ImmutableMap.of(typeParam, t));
    }
    return result;
  }

  public JSType getArgumentsArrayType(JSType t) {
    if (this.arguments == null) {
      return this.UNKNOWN;
    }
    ImmutableList<String> typeParams = this.arguments.getTypeParameters();
    JSType result = this.arguments.getInstanceAsJSType();
    // typeParams can be != 1 in old externs files :-S
    if (typeParams.size() == 1) {
      String typeParam = Iterables.getOnlyElement(typeParams);
      result = result.substituteGenerics(ImmutableMap.of(typeParam, t));
    }
    return result;
  }

  public JSType getRegexpType() {
    return regexpInstance != null ? regexpInstance : this.UNKNOWN;
  }

  public JSType getNumberInstance() {
    return numberInstance != null ? numberInstance : this.NUMBER;
  }

  public JSType getBooleanInstance() {
    return booleanInstance != null ? booleanInstance : this.BOOLEAN;
  }

  public JSType getStringInstance() {
    return stringInstance != null ? stringInstance : this.STRING;
  }

  ObjectType getNumberInstanceObjType() {
    return numberInstanceObjtype != null
        ? numberInstanceObjtype : this.TOP_OBJECTTYPE;
  }

  ObjectType getBooleanInstanceObjType() {
    return booleanInstanceObjtype != null
        ? booleanInstanceObjtype : this.TOP_OBJECTTYPE;
  }

  ObjectType getStringInstanceObjType() {
    return stringInstanceObjtype != null
        ? stringInstanceObjtype : this.TOP_OBJECTTYPE;
  }

  public JSType getArgumentsArrayType() {
    return getArgumentsArrayType(this.UNKNOWN);
  }

  public void setArgumentsType(RawNominalType arguments) {
    this.arguments = arguments;
  }

  public void setFunctionType(RawNominalType builtinFunction) {
    this.builtinFunction = builtinFunction;
  }

  public void setObjectType(RawNominalType builtinObject) {
    this.builtinObject = builtinObject;
  }

  public void setArrayType(RawNominalType arrayType) {
    this.arrayType = arrayType;
  }

  public void setIObjectType(RawNominalType iObject) {
    this.iObject = iObject;
  }

  public void setRegexpInstance(JSType regexpInstance) {
    this.regexpInstance = regexpInstance;
  }

  public void setNumberInstance(JSType t) {
    Preconditions.checkState(numberInstance == null);
    Preconditions.checkNotNull(t);
    numberInstance = t;
    numberOrNumber = JSType.join(this.NUMBER, numberInstance);
    numberInstanceObjtype = Iterables.getOnlyElement(t.getObjs());
    if (stringInstance != null) {
      anyNumOrStr = JSType.join(numberOrNumber, stringOrString);
    }
  }

  public void setBooleanInstance(JSType t) {
    Preconditions.checkState(booleanInstance == null);
    Preconditions.checkNotNull(t);
    booleanInstance = t;
    booleanInstanceObjtype = Iterables.getOnlyElement(t.getObjs());
  }

  public void setStringInstance(JSType t) {
    Preconditions.checkState(stringInstance == null);
    Preconditions.checkNotNull(t);
    stringInstance = t;
    stringOrString = JSType.join(this.STRING, stringInstance);
    stringInstanceObjtype = Iterables.getOnlyElement(t.getObjs());
    if (numberInstance != null) {
      anyNumOrStr = JSType.join(numberOrNumber, stringOrString);
    }
  }

  public boolean isNumberScalarOrObj(JSType t) {
    if (numberOrNumber == null) {
      return t.isSubtypeOf(this.NUMBER);
    }
    return t.isSubtypeOf(numberOrNumber);
  }

  public boolean isStringScalarOrObj(JSType t) {
    if (numberOrNumber == null) {
      return t.isSubtypeOf(this.STRING);
    }
    return t.isSubtypeOf(stringOrString);
  }

  // This method is a bit ad-hoc, but it allows us to not make the boxed
  // instances (which are not final) public.
  public boolean isNumStrScalarOrObj(JSType t) {
    if (anyNumOrStr == null) {
      return t.isSubtypeOf(this.NUMBER_OR_STRING);
    }
    return t.isSubtypeOf(anyNumOrStr);
  }
}
