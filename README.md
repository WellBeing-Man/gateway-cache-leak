# gateway-leak

## python code need locust

#### pip install locust
#### locust -f RequestTest.py

----

#### NoLeakCacheRequestBodyFilterGatewayFilterFactory is No leak version
#### i just added a method removeCachedRequestBody(exchange);
#### see application.yml to switch caching filter