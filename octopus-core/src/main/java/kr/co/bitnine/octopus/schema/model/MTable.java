package kr.co.bitnine.octopus.schema.model;

import javax.jdo.annotations.IdGeneratorStrategy;
import javax.jdo.annotations.PersistenceCapable;
import javax.jdo.annotations.Persistent;
import javax.jdo.annotations.PrimaryKey;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@PersistenceCapable
public class MTable {
    @PrimaryKey
    @Persistent(valueStrategy= IdGeneratorStrategy.INCREMENT)
    long ID;

    String name;
    int type;
    String description;
    String schema_name;
    MSchema schema;

    @Persistent(mappedBy = "table")
    Collection<MColumn> columns;

    public MTable(String name, int type, String description, MSchema schema)
    {
        this.name = name;
        this.type = type;
        this.description = description;
        this.schema = schema;
    }

    public int getColumnCnt() {
        return columns.size();
    }

    public List<MColumn> getColumns()
    {
        return new ArrayList<MColumn>(columns);
    }

    public String getName() {
        return name;
    }
}

