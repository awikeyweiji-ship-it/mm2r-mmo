from app.worker import DurableWorker

if __name__=="__main__":
    DurableWorker().run_forever()
