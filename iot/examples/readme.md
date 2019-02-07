### Start and test managed IoT Project

* Create project

  ```
  oc new-project enmasse-infra || oc project enmasse-infra
  ```

* Install EnMasse with IoT services

  ```
  oc apply -f templates/build/enmasse-latest/install/bundles/enmasse-iot-with-standard-authservice/
  ```

* Create Managed IoT Project

  ```
  oc create -f iot/examples/iot-project-managed.yaml
  ```

* Create Messaging Consumer User

  ```
  oc create -f iot/examples/iot-user.yaml
  ```

* Register a device

  ```
  curl --insecure -X POST -i -H 'Content-Type: application/json' --data-binary '{"device-id": "4711"}' https://$(oc get routes device-registry --template='{{ .spec.host }}')/registration/enmasse-infra.managed
  ```

* Add credentials for a device

  ```
  PWD_HASH=$(echo -n "hono-secret" | openssl dgst -binary -sha512 | base64)
  
  curl --insecure -X POST -i -H 'Content-Type: application/json' --data-binary '{"device-id": "4711","type": "hashed-password","auth-id": "sensor1","secrets": [{"hash-function" : "sha-512","pwd-hash":"'$PWD_HASH'"}]}' https://$(oc get routes device-registry --template='{{ .spec.host }}')/credentials/enmasse-infra.managed
  ```

* Start a telemetry consumer

  ```
  oc get addressspace managed -o jsonpath={.status.endpointStatuses[?\(@.name==\'messaging\'\)].cert} | base64 -D > target/config/hono-demo-certs-jar/tls.crt
  
  mvn spring-boot:run -Drun.arguments=--hono.client.host=$(oc get addressspace managed -o jsonpath={.status.endpointStatuses[?\(@.name==\'messaging\'\)].externalHost}),--hono.client.port=443,--hono.client.username=consumer,--hono.client.password=foobar,--tenant.id=enmasse-infra.managed,--hono.client.trustStorePath=target/config/hono-demo-certs-jar/tls.crt
  ```

* Send telemetry message using HTTP

  ```
  curl --insecure -X POST -i -u sensor1@enmasse-infra.managed:hono-secret -H 'Content-Type: application/json' --data-binary '{"temp": 5}' https://$(oc get route iot-http-adapter --template='{{.spec.host}}')/telemetry
  ```

* Send telemetry message using MQTT

  This is an example using NodePort, so you need to know your cluster IP and use in the command

  ```
  mosquitto_pub -h ${cluster.ip} -p 31883 -u 'sensor1@enmasse-infra.managed' -P hono-secret -t telemetry -m '{"temp": 5}'
  ```

  In the future, we'll provide a proper examples based on the Openshift route with TLS and remove NodePort.