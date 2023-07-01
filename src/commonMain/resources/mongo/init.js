db.getSiblingDB('admin').auth(
    process.env.MONGO_INITDB_ROOT_USERNAME,
    process.env.MONGO_INITDB_ROOT_PASSWORD
);

db.createUser(
    {
        user: process.env.MONGO_USER,
        pwd: process.env.MONGO_PASSWORD,
        roles: [
            {
                role: "readWrite",
                db: process.env.MONGO_INITDB_DATABASE
            }
        ]
    }
);

db.sessions.createIndex(
    {
        "expireAt": 1
    },
    {
        expireAfterSeconds: 0,
        name: "ttl"
    }
);

db.sessions.createIndex(
    {
        "session.userId": 1
    }
);
