from __future__ import annotations
from abc import ABC, abstractmethod
from ..models import Resource, Decision

class OpportunityPlugin(ABC):
    slug: str
    title: str

    @abstractmethod
    def evaluate(self, resource: Resource) -> Decision: ...
