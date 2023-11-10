/*
 * Copyright 2003 - 2022 The eFaps Team
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
 *
 */
package org.efaps.esjp.graphql;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;

import org.apache.commons.lang3.StringUtils;
import org.efaps.admin.program.esjp.EFapsApplication;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.db.Instance;
import org.efaps.db.stmt.selection.Evaluator;
import org.efaps.eql.EQL;
import org.efaps.eql.builder.Print;
import org.efaps.eql.builder.Where;
import org.efaps.eql2.EQL2;
import org.efaps.eql2.IWhereElementTerm;
import org.efaps.eql2.impl.PrintQueryStatement;
import org.efaps.esjp.common.properties.PropertiesUtil;
import org.efaps.esjp.db.InstanceUtils;
import org.efaps.graphql.definition.ArgumentDef;
import org.efaps.graphql.definition.FieldDef;
import org.efaps.graphql.definition.ObjectDef;
import org.efaps.graphql.providers.DataFetcherProvider;
import org.efaps.graphql.providers.FieldType;
import org.efaps.util.DateTimeUtil;
import org.efaps.util.EFapsException;
import org.jfree.util.Log;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import graphql.execution.DataFetcherResult;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.FieldCoordinates;
import graphql.schema.GraphQLList;
import graphql.schema.GraphQLNamedType;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLType;
import graphql.schema.SelectedField;

@EFapsUUID("3c405c05-9940-4eea-8d59-959b4890f4b3")
@EFapsApplication("eFaps-GraphQL")
public abstract class BaseDataFetcher_Base
    implements DataFetcher<Object>
{

    private static final Logger LOG = LoggerFactory.getLogger(BaseDataFetcher.class);

    @Override
    public Object get(final DataFetchingEnvironment _environment)
        throws Exception
    {
        LOG.debug("Running BaseDataFetcher with: {}", _environment);
        final var resultBldr = DataFetcherResult.newResult();
        final List<Map<String, Object>> values = new ArrayList<>();
        final var fieldName = _environment.getFieldDefinition().getName();
        final var parentTypeName = _environment.getExecutionStepInfo().getObjectType().getName();
        final Optional<ObjectDef> baseObjectDefOpt = _environment.getGraphQlContext()
                        .getOrEmpty(parentTypeName);
        final var argumentDefs = new ArrayList<ArgumentDef>();
        if (baseObjectDefOpt.isPresent()) {
            final FieldDef fieldDef = baseObjectDefOpt.get().getFields().get(fieldName);
            if (fieldDef != null) {
                argumentDefs.addAll(fieldDef.getArguments());
            }
        }
        final String contextKey = DataFetcherProvider.contextKey(parentTypeName, fieldName);
        final var props = _environment.getGraphQlContext().getOrDefault(contextKey,
                        new HashMap<>());
        final var properties = new Properties();
        properties.putAll(props);
        final Map<Integer, String> types = PropertiesUtil.analyseProperty(properties, "Type", 0);
        final Map<Integer, String> linkFroms = PropertiesUtil.analyseProperty(properties, "LinkFrom", 0);
        final Map<Integer, String> staticKeys = PropertiesUtil.analyseProperty(properties, "StaticKey", 0);
        final Map<Integer, String> staticValues = PropertiesUtil.analyseProperty(properties, "StaticValue", 0);

        final var localContext = getLocalContext(_environment);

        GraphQLType graphQLType = _environment.getExecutionStepInfo().getFieldDefinition().getType();
        if (graphQLType instanceof GraphQLList) {
            graphQLType = ((GraphQLList) graphQLType).getWrappedType();
        }
        final var graphTypeName = ((GraphQLNamedType) graphQLType).getName();
        final Optional<ObjectDef> objectDefOpt = _environment.getGraphQlContext().getOrEmpty(graphTypeName);
        if (objectDefOpt.isPresent()) {
            final var objectDef = objectDefOpt.get();
            Print print = null;
            if (!types.isEmpty()) {
                final var query = EQL.builder().print()
                                .query(types.values().toArray(new String[types.values().size()]));
                Where where = null;
                if (!linkFroms.isEmpty()) {
                    final Instance parentInstance = (Instance) ((Map<?, ?>) _environment.getSource())
                                    .get("currentInstance");
                    if (InstanceUtils.isValid(parentInstance)) {
                        where = query.where();
                        where.attr(linkFroms.values().iterator().next()).eq(parentInstance);
                    }
                }
                for (final var entry : _environment.getArguments().entrySet()) {
                    final var argDefOpt = argumentDefs.stream().filter(en -> en.getName().equals(entry.getKey()))
                                    .findFirst();
                    if (argDefOpt.isPresent()) {
                        final var argDef = argDefOpt.get();
                        if (where == null) {
                            where = query.where();
                        } else {
                            where.and();
                        }

                        final var stmt = (PrintQueryStatement) EQL2.parse("print query type TYPE where "
                                        + String.format(argDef.getWhereStmt(), entry.getValue())
                                        + " select attribute[OID]");
                        final var tmp = where.attr(((IWhereElementTerm) stmt.getQuery().getWhere().getTerms(0))
                                        .element().getAttribute());

                        final var value = convertArgument(argDef.getFieldType(), entry.getValue());
                        switch (((IWhereElementTerm) stmt.getQuery().getWhere().getTerms(0)).element()
                                        .getComparison()) {
                            case EQUAL:
                                tmp.eq(value);
                                break;
                            case GREATER:
                                tmp.greater(value);
                                break;
                            case GREATEREQ:
                                tmp.greaterOrEq(value);
                                break;
                            case LESS:
                                tmp.less(value);
                                break;
                            case LESSEQ:
                                tmp.lessOrEq(value);
                                break;
                            case LIKE:
                                tmp.like(value);
                                break;
                            case IN:
                                tmp.in(value);
                                break;
                            case NOTIN:
                                tmp.notin(value);
                                break;
                            case UNEQUAL:
                                tmp.uneq(value);
                                break;
                            default:
                                Log.error("Not working");
                        }
                    }
                }
                print = query.select();
            } else {
                // if the type is empty --> check if we got a list of instances
                // or one instance
                final var selectValue = ((Map<?, ?>) _environment.getSource()).get(fieldName);
                if (selectValue != null) {
                    if (selectValue instanceof List) {
                        @SuppressWarnings("unchecked") final var instances = ((List<Instance>) selectValue).stream()
                                        .filter(Objects::nonNull)
                                        .toArray(Instance[]::new);
                        if (instances.length > 0) {
                            print = EQL.builder().print(instances);
                        }
                    } else if (selectValue instanceof Instance) {
                        print = EQL.builder().print((Instance) selectValue);
                    }
                }
            }
            if (print != null) {
                // get the first level of fields (ImmediateFields)
                for (final var selectedField : _environment.getSelectionSet().getImmediateFields()) {
                    if (objectDef.getFields().containsKey(selectedField.getName())) {
                        final boolean hasDataFetcher = _environment.getGraphQLSchema().getCodeRegistry().hasDataFetcher(
                                        FieldCoordinates.coordinates(graphTypeName, selectedField.getName()));
                        if ((selectedField.getType() instanceof GraphQLObjectType
                                        || selectedField.getType() instanceof GraphQLList) && !hasDataFetcher) {
                            addChildSelect(_environment, selectedField, print, objectDef, "");
                        } else {
                            final FieldDef fieldDef = objectDef.getFields().get(selectedField.getName());
                            if (StringUtils.isNotBlank(fieldDef.getSelect())) {
                                print.select(fieldDef.getSelect()).as(selectedField.getFullyQualifiedName());
                            }
                        }
                    }
                }
                final var eval = print.evaluate();
                while (eval.next()) {
                    final var map = new HashMap<String, Object>();
                    for (final var entry : staticKeys.entrySet()) {
                        map.put(entry.getValue(), staticValues.get(entry.getKey()));
                    }
                    for (final var selectedField : _environment.getSelectionSet().getImmediateFields()) {
                        final boolean hasDataFetcher = _environment.getGraphQLSchema().getCodeRegistry().hasDataFetcher(
                                        FieldCoordinates.coordinates(graphTypeName, selectedField.getName()));
                        if ((selectedField.getType() instanceof GraphQLObjectType
                                        || selectedField.getType() instanceof GraphQLList) && !hasDataFetcher) {
                            map.put(selectedField.getName(), getChildValue(_environment, selectedField, eval));
                        } else if (!map.containsKey(selectedField.getName())) {
                            map.put(selectedField.getName(),
                                            eval.get(selectedField.getFullyQualifiedName()));
                        }
                    }
                    map.put("currentInstance", eval.inst());
                    values.add(map);
                }
            }
        }
        return resultBldr.data(values)
                        .localContext(localContext)
                        .build();
    }

    @SuppressWarnings("unchecked")
    protected Object getChildValue(final DataFetchingEnvironment environment,
                                   final SelectedField selectedField,
                                   final Evaluator eval)
        throws EFapsException
    {
        Object ret = null;
        if (selectedField.getType() instanceof GraphQLList) {
            LOG.debug("Get child value for ObjectList: {}", selectedField);
            final var valueList = new ArrayList<Map<String, Object>>();
            final var tmp = new HashMap<String, List<Object>>();
            for (final var childField : environment.getSelectionSet().getFields(selectedField.getName() + "/*")) {
                if (childField.getType() instanceof GraphQLObjectType) {
                    tmp.put(childField.getName(), (List<Object>) getChildValue(environment, childField, eval));
                } else {
                    tmp.put(childField.getName(),
                                    eval.get(childField.getFullyQualifiedName()));
                }
            }
            LOG.debug(" tmp result: {}", tmp);

            for (final var entry : tmp.entrySet()) {
                if (valueList.isEmpty()) {
                    int counter = 0;
                    while (counter < entry.getValue().size()) {
                        valueList.add(new HashMap<>());
                        counter++;
                    }
                }
                final var valueIter = valueList.iterator();
                for (final Object element : entry.getValue()) {
                    valueIter.next().put(entry.getKey(), element);
                }
            }
            LOG.debug(" valueList result: {}", valueList);
            final var valIter = valueList.iterator();
            while (valIter.hasNext()) {
                final var map =  valIter.next();
                if (map.values().stream().noneMatch(d -> d != null)) {
                    valIter.remove();
                }
            }
            ret = valueList;
        } else {
            final var map = new HashMap<String, Object>();
            for (final var childField : environment.getSelectionSet().getFields(selectedField.getName() + "/*")) {
                if (childField.getType() instanceof GraphQLObjectType) {
                    map.put(childField.getName(), getChildValue(environment, childField, eval));
                } else {
                    map.put(childField.getName(),
                                    eval.get(childField.getFullyQualifiedName()));
                }
            }
            ret = map;
        }
        return ret;
    }

    protected void addChildSelect(final DataFetchingEnvironment environment,
                                  final SelectedField selectedField,
                                  final Print print,
                                  final ObjectDef parentObjectDef,
                                  final String baseSelect)
    {
        final FieldDef fieldDef = parentObjectDef.getFields().get(selectedField.getName());
        var select = baseSelect;
        if (StringUtils.isNotBlank(fieldDef.getSelect())) {
            if (StringUtils.isNotBlank(select)) {
                select = select + ".";
            }
            select = select + fieldDef.getSelect();
        }
        String typeName;
        if (selectedField.getType() instanceof GraphQLList) {
            typeName = ((GraphQLObjectType) ((GraphQLList) selectedField.getType()).getWrappedType()).getName();
        } else {
            typeName = ((GraphQLObjectType) selectedField.getType()).getName();
        }

        final Optional<ObjectDef> currentObjOpt = environment.getGraphQlContext().getOrEmpty(typeName);
        if (currentObjOpt.isPresent()) {
            final var currentObj = currentObjOpt.get();
            for (final var childField : environment.getSelectionSet().getFields(selectedField.getName() + "/*")) {
                if (childField.getType() instanceof GraphQLObjectType) {
                    addChildSelect(environment, selectedField, print, currentObj, select);
                } else {
                    final FieldDef chieldFieldDef = currentObj.getFields().get(childField.getName());
                    if (StringUtils.isNotBlank(chieldFieldDef.getSelect())) {
                        var sel = select;
                        if (StringUtils.isNotBlank(sel)) {
                            sel = sel + ".";
                        }
                        print.select(sel + chieldFieldDef.getSelect()).as(childField.getFullyQualifiedName());
                    }
                }
            }
        }
    }

    protected Map<String, Object> getLocalContext(final DataFetchingEnvironment _environment)
    {
        Map<String, Object> ret;
        if (_environment.getLocalContext() == null) {
            ret = new HashMap<>();
        } else {
            ret = _environment.getLocalContext();
        }
        return ret;
    }

    protected String convertArgument(final FieldType fieldType,
                                     final Object value)
        throws EFapsException
    {
        String ret;
        switch (fieldType) {
            case DATETIME:
                // dateTimes are stored without a timezone in the database ->
                // therefore the given datetime must be converted to the
                // timezone of the database
                if (value instanceof OffsetDateTime) {
                    ret = ((OffsetDateTime) value).atZoneSameInstant(DateTimeUtil.getDBZoneId()).toLocalDateTime()
                                    .toString();
                } else {
                    ret = Objects.toString(value);
                }
                break;
            default:
                ret = Objects.toString(value);
        }
        return ret;
    }
}
