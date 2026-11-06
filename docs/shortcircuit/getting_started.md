# Getting started

## Maven dependencies

Add the implementation module to your project. Add the CGMES module too if your networks are imported from CGMES files
with short-circuit data.

```xml
<dependency>
    <groupId>com.powsybl</groupId>
    <artifactId>powsybl-open-sc-implementation</artifactId>
    <version>${powsybl-open-sc.version}</version>
</dependency>
<dependency>
    <groupId>com.powsybl</groupId>
    <artifactId>powsybl-open-sc-cgmes</artifactId>
    <version>${powsybl-open-sc.version}</version>
</dependency>
```

The provider is registered with `@AutoService`, under the name `OpenShortCircuit`.

## Running a short-circuit analysis

```java
Network network = ...;

// A three-phase fault on bus "B1", with a fault impedance of 0 Ohm
List<Fault> faults = List.of(new BusFault("F1", "B1"));

ShortCircuitParameters parameters = new ShortCircuitParameters()
        .setStudyType(StudyType.SUB_TRANSIENT)
        .setWithVoltageResult(true);

// OpenSC specific parameters, see the parameters page
parameters.addExtension(OpenShortCircuitParameters.class, new OpenShortCircuitParameters()
        .setWithResistances(true)
        .setWithCapacities(true));

ShortCircuitAnalysisResult result = ShortCircuitAnalysis.find("OpenShortCircuit")
        .run(network, faults, parameters, LocalComputationManager.getDefault(), List.of());

for (FaultResult faultResult : result.getFaultResults()) {
    MagnitudeFaultResult magnitudeResult = (MagnitudeFaultResult) faultResult;
    System.out.println(faultResult.getFault().getId() + ": " + magnitudeResult.getCurrent() + " A");
}
```

The provider can also be created directly. Use the constructor with a norm to apply the IEC 60909 correction factors:

```java
ShortCircuitAnalysisProvider provider = new OpenShortCircuitProvider(new ShortCircuitNormIec());
```

The content of the results is described in [Faults and results](faults_and_results.md).
