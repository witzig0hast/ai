from fastapi import APIRouter, Depends, HTTPException, status
from sqlmodel import Session, select

from app.core.security import require_device_token
from app.database import get_session
from app.models.agent import Agent, AgentCreate, AgentRead, AgentUpdate

router = APIRouter(prefix="/api/agents", tags=["agents"], dependencies=[Depends(require_device_token)])


@router.get("", response_model=list[AgentRead])
def list_agents(session: Session = Depends(get_session)):
    return session.exec(select(Agent)).all()


@router.post("", response_model=AgentRead, status_code=status.HTTP_201_CREATED)
def create_agent(body: AgentCreate, session: Session = Depends(get_session)):
    if session.get(Agent, body.id):
        raise HTTPException(status.HTTP_400_BAD_REQUEST, "Agent id already exists")
    agent = Agent.model_validate(body)
    session.add(agent)
    session.commit()
    session.refresh(agent)
    return agent


@router.get("/{agent_id}", response_model=AgentRead)
def get_agent(agent_id: str, session: Session = Depends(get_session)):
    agent = session.get(Agent, agent_id)
    if not agent:
        raise HTTPException(status.HTTP_404_NOT_FOUND, "Agent not found")
    return agent


@router.put("/{agent_id}", response_model=AgentRead)
def update_agent(agent_id: str, body: AgentUpdate, session: Session = Depends(get_session)):
    agent = session.get(Agent, agent_id)
    if not agent:
        raise HTTPException(status.HTTP_404_NOT_FOUND, "Agent not found")
    for field, value in body.model_dump(exclude_unset=True).items():
        setattr(agent, field, value)
    session.add(agent)
    session.commit()
    session.refresh(agent)
    return agent


@router.delete("/{agent_id}", status_code=status.HTTP_204_NO_CONTENT)
def delete_agent(agent_id: str, session: Session = Depends(get_session)):
    agent = session.get(Agent, agent_id)
    if not agent:
        raise HTTPException(status.HTTP_404_NOT_FOUND, "Agent not found")
    if agent.is_default:
        raise HTTPException(status.HTTP_400_BAD_REQUEST, "Cannot delete the default agent")
    session.delete(agent)
    session.commit()
