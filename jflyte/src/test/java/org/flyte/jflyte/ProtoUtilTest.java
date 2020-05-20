/*
 * Copyright 2020 Spotify AB.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.flyte.jflyte;

import static flyteidl.core.IdentifierOuterClass.ResourceType.LAUNCH_PLAN;
import static flyteidl.core.IdentifierOuterClass.ResourceType.TASK;
import static flyteidl.core.IdentifierOuterClass.ResourceType.WORKFLOW;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import flyteidl.core.Errors;
import flyteidl.core.IdentifierOuterClass;
import flyteidl.core.Interface;
import flyteidl.core.Literals;
import flyteidl.core.Tasks;
import flyteidl.core.Types;
import flyteidl.core.Workflow;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.flyte.api.v1.Binding;
import org.flyte.api.v1.BindingData;
import org.flyte.api.v1.Container;
import org.flyte.api.v1.Identifier;
import org.flyte.api.v1.KeyValuePair;
import org.flyte.api.v1.LaunchPlanIdentifier;
import org.flyte.api.v1.Literal;
import org.flyte.api.v1.Node;
import org.flyte.api.v1.OutputReference;
import org.flyte.api.v1.PartialTaskIdentifier;
import org.flyte.api.v1.Primitive;
import org.flyte.api.v1.Scalar;
import org.flyte.api.v1.SimpleType;
import org.flyte.api.v1.TaskIdentifier;
import org.flyte.api.v1.TaskNode;
import org.flyte.api.v1.TaskTemplate;
import org.flyte.api.v1.TypedInterface;
import org.flyte.api.v1.Variable;
import org.flyte.api.v1.WorkflowIdentifier;
import org.flyte.api.v1.WorkflowMetadata;
import org.flyte.api.v1.WorkflowTemplate;
import org.junit.Assert;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@SuppressWarnings("PreferJavaTimeOverload")
class ProtoUtilTest {
  private static final String DOMAIN = "development";
  private static final String PROJECT = "flyte-test";
  private static final String VERSION = "1";

  @ParameterizedTest
  @MethodSource("createDeserializePrimitivesArguments")
  void shouldDeserializePrimitives(Literals.Primitive input, Primitive expected) {
    assertThat(ProtoUtil.deserialize(input), equalTo(expected));
  }

  static Stream<Arguments> createDeserializePrimitivesArguments() {
    Instant now = Instant.now();
    long seconds = now.getEpochSecond();
    int nanos = now.getNano();

    return Stream.of(
        Arguments.of(
            Literals.Primitive.newBuilder().setInteger(123).build(), Primitive.ofInteger(123)),
        Arguments.of(
            Literals.Primitive.newBuilder().setFloatValue(123.0).build(), Primitive.ofFloat(123.0)),
        Arguments.of(
            Literals.Primitive.newBuilder().setStringValue("123").build(),
            Primitive.ofString("123")),
        Arguments.of(
            Literals.Primitive.newBuilder().setBoolean(true).build(), Primitive.ofBoolean(true)),
        Arguments.of(
            Literals.Primitive.newBuilder()
                .setDatetime(
                    com.google.protobuf.Timestamp.newBuilder()
                        .setSeconds(seconds)
                        .setNanos(nanos)
                        .build())
                .build(),
            Primitive.ofDatetime(Instant.ofEpochSecond(seconds, nanos))),
        Arguments.of(
            Literals.Primitive.newBuilder()
                .setDuration(
                    com.google.protobuf.Duration.newBuilder()
                        .setSeconds(seconds)
                        .setNanos(nanos)
                        .build())
                .build(),
            Primitive.ofDuration(Duration.ofSeconds(seconds, nanos))));
  }

  @Test
  void shouldSerializeLiteralMap() {
    Map<String, Literal> input =
        ImmutableMap.of("a", Literal.of(Scalar.of(Primitive.ofInteger(1337L))));
    Literals.Primitive expectedPrimitive =
        Literals.Primitive.newBuilder().setInteger(1337L).build();
    Literals.Scalar expectedScalar =
        Literals.Scalar.newBuilder().setPrimitive(expectedPrimitive).build();
    Literals.LiteralMap expected =
        Literals.LiteralMap.newBuilder()
            .putLiterals("a", Literals.Literal.newBuilder().setScalar(expectedScalar).build())
            .build();

    Literals.LiteralMap output = ProtoUtil.serializeLiteralMap(input);

    Assert.assertEquals(expected, output);
  }

  @Test
  void shouldSerializeOutputReference() {
    OutputReference input = OutputReference.builder().nodeId("node-id").var("var").build();
    Types.OutputReference expected =
        Types.OutputReference.newBuilder().setNodeId("node-id").setVar("var").build();

    Types.OutputReference output = ProtoUtil.serialize(input);

    Assert.assertEquals(expected, output);
  }

  @Test
  void shouldSerializeBindingData() {
    BindingData input = BindingData.of(Scalar.of(Primitive.ofInteger(1337L)));
    Literals.Scalar expectedScalar =
        Literals.Scalar.newBuilder()
            .setPrimitive(Literals.Primitive.newBuilder().setInteger(1337L).build())
            .build();
    Literals.BindingData expected =
        Literals.BindingData.newBuilder().setScalar(expectedScalar).build();

    Literals.BindingData output = ProtoUtil.serialize(input);

    Assert.assertEquals(expected, output);
  }

  @Test
  void shouldSerializeLaunchPlanIdentifiers() {
    String name = "launch-plan-a";
    LaunchPlanIdentifier id =
        LaunchPlanIdentifier.builder()
            .domain(DOMAIN)
            .project(PROJECT)
            .name(name)
            .version(VERSION)
            .build();

    IdentifierOuterClass.Identifier serializedId = ProtoUtil.serialize(id);

    assertThat(
        serializedId,
        equalTo(
            IdentifierOuterClass.Identifier.newBuilder()
                .setResourceType(LAUNCH_PLAN)
                .setDomain(DOMAIN)
                .setProject(PROJECT)
                .setName(name)
                .setVersion(VERSION)
                .build()));
  }

  @Test
  void shouldSerializeTaskIdentifiers() {
    String name = "task-a";
    TaskIdentifier id =
        TaskIdentifier.builder()
            .domain(DOMAIN)
            .project(PROJECT)
            .name(name)
            .version(VERSION)
            .build();

    IdentifierOuterClass.Identifier serializedId = ProtoUtil.serialize(id);

    assertThat(
        serializedId,
        equalTo(
            IdentifierOuterClass.Identifier.newBuilder()
                .setResourceType(TASK)
                .setDomain(DOMAIN)
                .setProject(PROJECT)
                .setName(name)
                .setVersion(VERSION)
                .build()));
  }

  @Test
  void shouldSerializeWorkflowIdentifiers() {
    String name = "workflow-a";
    WorkflowIdentifier id =
        WorkflowIdentifier.builder()
            .domain(DOMAIN)
            .project(PROJECT)
            .name(name)
            .version(VERSION)
            .build();

    IdentifierOuterClass.Identifier serializedId = ProtoUtil.serialize(id);

    assertThat(
        serializedId,
        equalTo(
            IdentifierOuterClass.Identifier.newBuilder()
                .setResourceType(WORKFLOW)
                .setDomain(DOMAIN)
                .setProject(PROJECT)
                .setName(name)
                .setVersion(VERSION)
                .build()));
  }

  @Test
  void shouldThrowIllegalArgumentExceptionForUnknownImplementationsOfIdentifiers() {
    Identifier id = new UnknownIdentifier();

    Assertions.assertThrows(IllegalArgumentException.class, () -> ProtoUtil.serialize(id));
  }

  @Test
  void shouldSerializeTaskTemplate() {
    Container container =
        Container.builder()
            .command(ImmutableList.of("echo"))
            .args(ImmutableList.of("hello world"))
            .env(ImmutableList.of(KeyValuePair.of("key", "value")))
            .image("alpine:3.7")
            .build();

    Variable stringVar = ApiUtils.createVar(SimpleType.STRING);
    Variable integerVar = ApiUtils.createVar(SimpleType.INTEGER);

    TypedInterface interface_ =
        TypedInterface.builder()
            .inputs(ImmutableMap.of("x", stringVar))
            .outputs(ImmutableMap.of("y", integerVar))
            .build();

    TaskTemplate template =
        TaskTemplate.builder().container(container).interface_(interface_).build();

    Tasks.TaskTemplate serializedTemplate = ProtoUtil.serialize(template);

    assertThat(
        serializedTemplate,
        equalTo(
            Tasks.TaskTemplate.newBuilder()
                .setContainer(
                    Tasks.Container.newBuilder()
                        .setImage("alpine:3.7")
                        .addCommand("echo")
                        .addArgs("hello world")
                        .addEnv(
                            Literals.KeyValuePair.newBuilder()
                                .setKey("key")
                                .setValue("value")
                                .build())
                        .build())
                .setMetadata(
                    Tasks.TaskMetadata.newBuilder()
                        .setRuntime(
                            Tasks.RuntimeMetadata.newBuilder()
                                .setType(Tasks.RuntimeMetadata.RuntimeType.FLYTE_SDK)
                                .setFlavor(ProtoUtil.RUNTIME_FLAVOR)
                                .setVersion(ProtoUtil.RUNTIME_VERSION)
                                .build())
                        .build())
                .setInterface(
                    Interface.TypedInterface.newBuilder()
                        .setInputs(
                            Interface.VariableMap.newBuilder()
                                .putVariables(
                                    "x",
                                    Interface.Variable.newBuilder()
                                        .setType(
                                            Types.LiteralType.newBuilder()
                                                .setSimple(Types.SimpleType.STRING)
                                                .build())
                                        .build())
                                .build())
                        .setOutputs(
                            Interface.VariableMap.newBuilder()
                                .putVariables(
                                    "y",
                                    Interface.Variable.newBuilder()
                                        .setType(
                                            Types.LiteralType.newBuilder()
                                                .setSimple(Types.SimpleType.INTEGER)
                                                .build())
                                        .build())
                                .build())
                        .build())
                .setType(ProtoUtil.TASK_TYPE)
                .build()));
  }

  @Test
  void shouldSerializeWorkflowTemplate() {
    Node nodeA = createNode("a").toBuilder().upstreamNodeIds(singletonList("b")).build();
    Node nodeB = createNode("b");
    WorkflowMetadata metadata = WorkflowMetadata.builder().build();
    TypedInterface interface_ =
        TypedInterface.builder().inputs(emptyMap()).outputs(emptyMap()).build();
    WorkflowTemplate template =
        WorkflowTemplate.builder()
            .nodes(Arrays.asList(nodeA, nodeB))
            .metadata(metadata)
            .interface_(interface_)
            .outputs(emptyList())
            .build();

    Workflow.Node expectedNode1 =
        Workflow.Node.newBuilder()
            .setId("a")
            .addUpstreamNodeIds("b")
            .setTaskNode(
                Workflow.TaskNode.newBuilder()
                    .setReferenceId(
                        IdentifierOuterClass.Identifier.newBuilder()
                            .setResourceType(TASK)
                            .setDomain(DOMAIN)
                            .setProject(PROJECT)
                            .setName("task-a")
                            .setVersion("version-a")
                            .build())
                    .build())
            .addInputs(
                Literals.Binding.newBuilder()
                    .setVar("input-name-a")
                    .setBinding(
                        Literals.BindingData.newBuilder()
                            .setScalar(
                                Literals.Scalar.newBuilder()
                                    .setPrimitive(
                                        Literals.Primitive.newBuilder()
                                            .setStringValue("input-scalar-a")
                                            .build())
                                    .build())
                            .build())
                    .build())
            .build();

    Workflow.Node expectedNode2 =
        Workflow.Node.newBuilder()
            .setId("b")
            .setTaskNode(
                Workflow.TaskNode.newBuilder()
                    .setReferenceId(
                        IdentifierOuterClass.Identifier.newBuilder()
                            .setResourceType(TASK)
                            .setDomain(DOMAIN)
                            .setProject(PROJECT)
                            .setName("task-b")
                            .setVersion("version-b")
                            .build())
                    .build())
            .addInputs(
                Literals.Binding.newBuilder()
                    .setVar("input-name-b")
                    .setBinding(
                        Literals.BindingData.newBuilder()
                            .setScalar(
                                Literals.Scalar.newBuilder()
                                    .setPrimitive(
                                        Literals.Primitive.newBuilder()
                                            .setStringValue("input-scalar-b")
                                            .build())
                                    .build())
                            .build())
                    .build())
            .build();

    Workflow.WorkflowTemplate serializedTemplate = ProtoUtil.serialize(template);

    assertThat(
        serializedTemplate,
        equalTo(
            Workflow.WorkflowTemplate.newBuilder()
                .setMetadata(Workflow.WorkflowMetadata.newBuilder().build())
                .setInterface(
                    Interface.TypedInterface.newBuilder()
                        .setOutputs(Interface.VariableMap.newBuilder().build())
                        .setInputs(Interface.VariableMap.newBuilder().build())
                        .build())
                .addNodes(expectedNode1)
                .addNodes(expectedNode2)
                .build()));
  }

  @ParameterizedTest
  @MethodSource("createSerializePrimitivesArguments")
  void shouldSerializePrimitives(Primitive input, Literals.Primitive expected) {
    assertThat(ProtoUtil.serialize(input), equalTo(expected));
  }

  static Stream<Arguments> createSerializePrimitivesArguments() {
    Instant now = Instant.now();
    long seconds = now.getEpochSecond();
    int nanos = now.getNano();

    return Stream.of(
        Arguments.of(
            Primitive.ofInteger(123), Literals.Primitive.newBuilder().setInteger(123).build()),
        Arguments.of(
            Primitive.ofFloat(123.0), Literals.Primitive.newBuilder().setFloatValue(123.0).build()),
        Arguments.of(
            Primitive.ofString("123"),
            Literals.Primitive.newBuilder().setStringValue("123").build()),
        Arguments.of(
            Primitive.ofBoolean(true), Literals.Primitive.newBuilder().setBoolean(true).build()),
        Arguments.of(
            Primitive.ofDatetime(Instant.ofEpochSecond(seconds, nanos)),
            Literals.Primitive.newBuilder()
                .setDatetime(
                    com.google.protobuf.Timestamp.newBuilder()
                        .setSeconds(seconds)
                        .setNanos(nanos)
                        .build())
                .build()),
        Arguments.of(
            Primitive.ofDuration(Duration.ofSeconds(seconds, nanos)),
            Literals.Primitive.newBuilder()
                .setDuration(
                    com.google.protobuf.Duration.newBuilder()
                        .setSeconds(seconds)
                        .setNanos(nanos)
                        .build())
                .build()));
  }

  @Test
  void shouldSerializeThrowable() {
    // for now, we have very simple implementation
    // proto for ErrorDocument isn't well documented as well

    Throwable e = new RuntimeException("oops");

    Errors.ErrorDocument errorDocument = ProtoUtil.serialize(e);
    Errors.ContainerError error = errorDocument.getError();

    assertThat(error.getKind(), equalTo(Errors.ContainerError.Kind.NON_RECOVERABLE));
    assertThat(error.getMessage(), containsString(e.getMessage()));
    assertThat(error.getCode(), equalTo("SYSTEM:Unknown"));
  }

  private Node createNode(String id) {
    String taskName = "task-" + id;
    String version = "version-" + id;
    String input_name = "input-name-" + id;
    String input_scalar = "input-scalar-" + id;

    TaskNode taskNode =
        TaskNode.builder()
            .referenceId(
                PartialTaskIdentifier.builder()
                    .domain(DOMAIN)
                    .project(PROJECT)
                    .name(taskName)
                    .version(version)
                    .build())
            .build();
    List<Binding> inputs =
        singletonList(
            Binding.builder()
                .var_(input_name)
                .binding(BindingData.of(Scalar.of(Primitive.ofString(input_scalar))))
                .build());

    return Node.builder()
        .id(id)
        .taskNode(taskNode)
        .inputs(inputs)
        .upstreamNodeIds(emptyList())
        .build();
  }

  private static class UnknownIdentifier implements Identifier {

    @Override
    public String domain() {
      return null;
    }

    @Override
    public String project() {
      return null;
    }

    @Override
    public String name() {
      return null;
    }

    @Override
    public String version() {
      return null;
    }
  }
}
