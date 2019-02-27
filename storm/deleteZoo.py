import kazoo
from kazoo.client import KazooClient

#zk = KazooClient(hosts='localhost:2181,10.10.21.1:2181,10.10.21.2:2181,10.10.21.3:2181,10.10.21.4:2181,10.10.21.5:2181,10.10.21.6:2181,10.10.21.7:2181')
zk = KazooClient(hosts='localhost:2181')
zk.start()
for child in zk.get_children('/'):
    print(child)
    if child.startswith('storm'):
        #zk.delete_async('/' + child)
        zk.delete(path='/' + child, recursive=True)
zk.stop()
