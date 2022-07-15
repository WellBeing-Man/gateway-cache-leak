from locust import HttpUser, task

bigBody="aa"*30000

class RequestTest(HttpUser):
    @task(1)
    def local_test(self):
        self.client.get(
                        url = "/cache",
                        json = { "body":bigBody }
                      )
